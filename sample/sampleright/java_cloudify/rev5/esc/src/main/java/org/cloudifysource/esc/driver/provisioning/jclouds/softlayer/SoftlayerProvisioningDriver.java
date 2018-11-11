/*
 * ******************************************************************************
 *  * Copyright (c) 2012 GigaSpaces Technologies Ltd. All rights reserved
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  ******************************************************************************
 */

package org.cloudifysource.esc.driver.provisioning.jclouds.softlayer;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.cloudifysource.domain.cloud.compute.ComputeTemplate;
import org.cloudifysource.esc.driver.provisioning.CloudProvisioningException;
import org.cloudifysource.esc.driver.provisioning.ComputeDriverConfiguration;
import org.cloudifysource.esc.driver.provisioning.jclouds.DefaultProvisioningDriver;
import org.cloudifysource.esc.util.Utils;
import org.jclouds.softlayer.compute.functions.VirtualGuestToNodeMetadata;
import org.jclouds.softlayer.compute.functions.VirtualGuestToReducedNodeMetaData;

import java.util.Set;

/**
 * This driver injects a custom module to jclouds in order to boost poor performance on softlayer.
 *
 * @author Eli Polonsky
 * @since 2.7.0
 */

public class SoftlayerProvisioningDriver extends DefaultProvisioningDriver {

    private boolean bareMetal;

    @Override
    public void setConfig(final ComputeDriverConfiguration configuration) throws CloudProvisioningException {

        ComputeTemplate computeTemplate =
                configuration.getCloud().getCloudCompute().getTemplates().get(configuration.getCloudTemplate());
        bareMetal = Utils.getBoolean(computeTemplate.getCustom()
                .get("org.cloudifysource.softlayer.bmi"), false);
        if (bareMetal) {
            configuration.getCloud().getProvider().setProvider("softlayer-bmi");
        }
        super.setConfig(configuration);
    }

    @Override
    public Set<Module> setupModules() {
        Set<Module> modules = super.setupModules();
        if (!bareMetal) {
            modules.add(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(VirtualGuestToNodeMetadata.class).to(VirtualGuestToReducedNodeMetaData.class);
                }
            });
        }
        return modules;
    }
}
