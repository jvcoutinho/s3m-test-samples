/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.logging;

import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.logging.internal.*;
import org.gradle.util.TimeProvider;
import org.gradle.util.TrueTimeProvider;

/**
 * A {@link org.gradle.api.internal.project.ServiceRegistry} implementation which provides the logging services.
 */
public class LoggingServiceRegistry extends DefaultServiceRegistry {
    private TextStreamOutputEventListener stdoutListener;

    public LoggingServiceRegistry() {
        stdoutListener = new TextStreamOutputEventListener(get(OutputEventListener.class));
    }

    protected TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }
    
    protected StdOutLoggingSystem createStdOutLoggingSystem() {
        return new StdOutLoggingSystem(stdoutListener, get(TimeProvider.class));
    }

    protected StyledTextOutputFactory createStyledTextOutputFactory() {
        return new DefaultStyledTextOutputFactory(stdoutListener, get(TimeProvider.class));
    }

    protected StdErrLoggingSystem createStdErrLoggingSystem() {
        return new StdErrLoggingSystem(new TextStreamOutputEventListener(get(OutputEventListener.class)), get(TimeProvider.class));
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(new ProgressLoggingBridge(get(OutputEventListener.class)), get(TimeProvider.class));
    }
    
    protected LoggingManagerFactory createLoggingManagerFactory() {
        OutputEventRenderer renderer = get(OutputEventRenderer.class);
        Slf4jLoggingConfigurer slf4jConfigurer = new Slf4jLoggingConfigurer(renderer);
        LoggingConfigurer compositeConfigurer = new DefaultLoggingConfigurer(renderer, slf4jConfigurer, new JavaUtilLoggingConfigurer());
        return new DefaultLoggingManagerFactory(compositeConfigurer, renderer, get(StdOutLoggingSystem.class), get(StdErrLoggingSystem.class));
    }
    
    protected OutputEventRenderer createOutputEventRenderer() {
        return new OutputEventRenderer().addStandardOutputAndError();
    }
}
