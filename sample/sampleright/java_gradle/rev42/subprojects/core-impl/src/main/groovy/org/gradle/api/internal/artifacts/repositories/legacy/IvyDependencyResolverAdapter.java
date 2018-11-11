/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.repositories.legacy;

import com.google.common.collect.ImmutableSet;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.resolution.SoftwareArtifact;
import org.gradle.api.internal.artifacts.MavenClassifierArtifactScheme;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.metadata.*;
import org.gradle.api.internal.artifacts.resolution.ComponentMetaDataArtifact;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;
import java.util.Set;

/**
 * A {@link org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository} wrapper around an Ivy {@link DependencyResolver}.
 */
public class IvyDependencyResolverAdapter implements ConfiguredModuleVersionRepository, IvyAwareModuleVersionRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(IvyDependencyResolverAdapter.class);
    private final DownloadOptions downloadOptions = new DownloadOptions();
    private final String identifier;
    private final DependencyResolver resolver;
    private ResolveData resolveData;

    public IvyDependencyResolverAdapter(DependencyResolver resolver) {
        this.resolver = resolver;
        identifier = DependencyResolverIdentifier.forIvyResolver(resolver);
    }

    public String getId() {
        return identifier;
    }

    public String getName() {
        return resolver.getName();
    }

    @Override
    public String toString() {
        return String.format("Repository '%s'", resolver.getName());
    }

    public boolean isLocal() {
        return resolver.getRepositoryCacheManager() instanceof LocalFileRepositoryCacheManager;
    }

    public void setSettings(IvySettings settings) {
        settings.addResolver(resolver);
    }

    public void setResolveData(ResolveData resolveData) {
        this.resolveData = resolveData;
    }

    public boolean isDynamicResolveMode() {
        return false;
    }

    public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        IvyContext.getContext().setResolveData(resolveData);
        try {
            ResolvedModuleRevision revision = resolver.getDependency(dependency.getDescriptor(), resolveData);
            if (revision == null) {
                result.listed(new DefaultModuleVersionListing());
            } else {
                result.listed(new DefaultModuleVersionListing(revision.getId().getRevision()));
            }
        } catch (ParseException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        IvyContext.getContext().setResolveData(resolveData);
        try {
            ResolvedModuleRevision revision = resolver.getDependency(dependency.getDescriptor(), resolveData);
            if (revision == null) {
                LOGGER.debug("Performed resolved of module '{}' in repository '{}': not found", dependency.getRequested(), getName());
                result.missing();
            } else {
                LOGGER.debug("Performed resolved of module '{}' in repository '{}': found", dependency.getRequested(), getName());
                ModuleDescriptorAdapter metaData = new ModuleDescriptorAdapter(revision.getDescriptor());
                metaData.setChanging(isChanging(revision));
                result.resolved(metaData, null);
            }
        } catch (ParseException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        Artifact ivyArtifact = createIvyArtifact((ModuleVersionArtifactMetaData) artifact);
        ArtifactDownloadReport artifactDownloadReport = resolver.download(new Artifact[]{ivyArtifact}, downloadOptions).getArtifactReport(ivyArtifact);
        if (downloadFailed(artifactDownloadReport)) {
            if (artifactDownloadReport instanceof EnhancedArtifactDownloadReport) {
                EnhancedArtifactDownloadReport enhancedReport = (EnhancedArtifactDownloadReport) artifactDownloadReport;
                result.failed(new ArtifactResolveException(artifact.getId(), enhancedReport.getFailure()));
            } else {
                result.failed(new ArtifactResolveException(artifact.getId(), artifactDownloadReport.getDownloadDetails()));
            }
            return;
        }

        File localFile = artifactDownloadReport.getLocalFile();
        if (localFile != null) {
            result.resolved(localFile);
        } else {
            result.notFound(artifact.getId());
        }
    }

    private Artifact createIvyArtifact(ModuleVersionArtifactMetaData artifact) {
        // TODO:DAZ Should really be looking up the Ivy Artifact from the ModuleDescriptor, to ensure we're not losing anything here, like URL or publication date.
        IvyArtifactName ivyName = artifact.getName();
        return new DefaultArtifact(IvyUtil.createModuleRevisionId(artifact.getModuleVersion()), null, ivyName.getName(), ivyName.getType(), ivyName.getExtension(), ivyName.getAttributes());
    }

    public void resolveModuleArtifacts(ModuleVersionMetaData moduleMetaData, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        if (context instanceof ConfigurationResolveContext) {
            String configurationName = ((ConfigurationResolveContext) context).getConfigurationName();
            result.resolved(moduleMetaData.getConfiguration(configurationName).getArtifacts());
        } else {
            Class<? extends SoftwareArtifact> artifactType = ((ArtifactTypeResolveContext) context).getArtifactType();
            try {
                result.resolved(getCandidateArtifacts(moduleMetaData, artifactType));
            } catch (Exception e) {
                result.failed(new ArtifactResolveException(moduleMetaData.getId(), e));
            }
        }
    }

    private Set<ModuleVersionArtifactMetaData> getCandidateArtifacts(ModuleVersionMetaData module, Class<? extends SoftwareArtifact> artifactType) {
        if (artifactType == ComponentMetaDataArtifact.class) {
            Artifact metadataArtifact = module.getDescriptor().getMetadataArtifact();
            return ImmutableSet.<ModuleVersionArtifactMetaData>of(new DefaultModuleVersionArtifactMetaData(module, metadataArtifact));
        }

        return new MavenClassifierArtifactScheme().get(module, artifactType);
    }

    private boolean downloadFailed(ArtifactDownloadReport artifactReport) {
        // Ivy reports FAILED with MISSING_ARTIFACT message when the artifact doesn't exist.
        return artifactReport.getDownloadStatus() == DownloadStatus.FAILED
                && !artifactReport.getDownloadDetails().equals(ArtifactDownloadReport.MISSING_ARTIFACT);
    }

    private boolean isChanging(ResolvedModuleRevision resolvedModuleRevision) {
        return new ChangingModuleDetector(resolver).isChangingModule(resolvedModuleRevision.getDescriptor());
    }
}
