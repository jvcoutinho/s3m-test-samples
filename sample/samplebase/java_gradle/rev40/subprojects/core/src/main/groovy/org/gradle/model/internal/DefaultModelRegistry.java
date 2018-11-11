/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal;


import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.gradle.api.Transformer;
import org.gradle.model.ModelPath;

import java.util.*;

import static org.gradle.util.CollectionUtils.collect;

public class DefaultModelRegistry implements ModelRegistry {

    private final Map<ModelPath, Object> store = new HashMap<ModelPath, Object>();

    private final Map<ModelPath, ModelCreation> creations = new HashMap<ModelPath, ModelCreation>();
    private final Multimap<ModelPath, ModelMutation<?>> mutators = ArrayListMultimap.create();

    private final List<ModelCreationListener> modelCreationListeners = new LinkedList<ModelCreationListener>();

    public <T> void create(String path, List<String> inputPaths, ModelCreator<T> creator) {
        ModelPath creationModelPath = ModelPath.path(path);
        if (creations.containsKey(creationModelPath)) {
            throw new IllegalStateException("creator already registered for '" + creationModelPath + "'");
        }
        if (store.containsKey(creationModelPath)) {
            throw new IllegalStateException("model already created for '" + creationModelPath + "'");
        }

        notifyCreationListeners(creationModelPath, creator);
        creations.put(creationModelPath, new ModelCreation(creator, toModelPaths(inputPaths)));
    }

    private static ImmutableList<ModelPath> toModelPaths(List<String> inputPaths) {
        return ImmutableList.copyOf(collect(inputPaths, new ToModelPath()));
    }

    public <T> void mutate(String path, List<String> inputPaths, ModelMutator<T> mutator) {
        mutate(path, new ModelMutation<T>(mutator, toModelPaths(inputPaths)));
    }

    public <T> void mutate(String path, ModelMutation<T> mutation) {
        ModelPath mutationModelPath = ModelPath.path(path);
        if (store.containsKey(mutationModelPath)) {
            throw new IllegalStateException("model '" + mutationModelPath + "' is finalized");
        }

        mutators.put(mutationModelPath, mutation);
    }

    public <T> T get(String path, Class<T> type) {
        ModelPath modelPath = ModelPath.path(path);
        Object model = getClosedModel(modelPath);

        if (type.isInstance(model)) {
            return type.cast(model);
        } else {
            throw new RuntimeException("Can't convert model at path '" + path + "' with type '" + model.getClass() + "' to target type '" + type + "'");
        }
    }

    public void registerListener(ModelCreationListener listener) {
        boolean remove;

        for (Map.Entry<ModelPath, ModelCreation> entry : creations.entrySet()) {
            remove = listener.onCreate(entry.getKey(), entry.getValue().getCreator().getType());
            if (remove) {
                return;
            }
        }

        modelCreationListeners.add(listener);
    }

    public void remove(String path) {
        if (creations.remove(ModelPath.path(path)) == null) {
            throw new RuntimeException("Tried to remove model " + path + " but it is not registered");
        }
    }

    private Set<ModelPath> getPromisedPaths() {
        return ImmutableSet.<ModelPath>builder().addAll(creations.keySet()).build();
    }

    private Object getClosedModel(ModelPath path) {
        if (store.containsKey(path)) {
            return store.get(path);
        }

        Object model = createModel(path);
        Collection<ModelMutation<?>> modelMutations = mutators.removeAll(path);
        for (ModelMutation modelMutation : modelMutations) {
            fireMutation(model, modelMutation);
        }

        // close all the child objects
        Set<ModelPath> promisedPaths = getPromisedPaths();
        for (ModelPath modelPath : promisedPaths) {
            if (path.isDirectChild(modelPath)) {
                getClosedModel(modelPath);
            }
        }

        store.put(path, model);

        return model;
    }

    private Object createModel(final ModelPath path) {
        ModelCreation creation = creations.remove(path);
        if (creation == null) {
            throw new IllegalStateException("No creator for '" + path + "'");
        }

        Inputs inputs = toInputs(creation.getInputPaths());
        Object created = creation.getCreator().create(inputs);
        store.put(path, created);
        return created;
    }

    private <T> void fireMutation(Object model, ModelMutation<T> modelMutation) {
        ModelMutator<T> mutator = modelMutation.getMutator();
        Inputs inputs = toInputs(modelMutation.getInputPaths());
        T cast = mutator.getType().cast(model);
        mutator.mutate(cast, inputs);
    }

    private Inputs toInputs(Iterable<ModelPath> inputPaths) {
        ImmutableList.Builder<?> builder = ImmutableList.builder();
        for (ModelPath inputPath : inputPaths) {
            builder.add(getClosedModel(inputPath));
        }
        return new DefaultInputs(builder.build());
    }

    private <T> void notifyCreationListeners(ModelPath path, ModelCreator<T> creator) {
        ListIterator<ModelCreationListener> modelCreationListenerListIterator = modelCreationListeners.listIterator();
        while (modelCreationListenerListIterator.hasNext()) {
            ModelCreationListener next = modelCreationListenerListIterator.next();
            boolean remove = next.onCreate(path, creator.getType());
            if (remove) {
                modelCreationListenerListIterator.remove();
            }
        }
    }

    private static class ModelCreation {

        private final ModelCreator<?> creator;
        private final ImmutableList<ModelPath> inputPaths;

        public ModelCreation(ModelCreator<?> creator, ImmutableList<ModelPath> inputPaths) {
            this.creator = creator;
            this.inputPaths = inputPaths;
        }

        public ModelCreator<?> getCreator() {
            return creator;
        }

        public ImmutableList<ModelPath> getInputPaths() {
            return inputPaths;
        }

    }

    private static class ToModelPath implements Transformer<ModelPath, String> {
        public ModelPath transform(String string) {
            return ModelPath.path(string);
        }
    }


}
