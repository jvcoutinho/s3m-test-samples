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
package org.gradle.foundation.ipc.basic;

import org.gradle.foundation.common.ObserverLord;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.exec.ExecHandle;
import org.gradle.util.exec.ExecHandleBuilder;

import java.io.ByteArrayOutputStream;

/**
 * This launches an application as a separate process then listens for messages from it. You implement the Protocol
 * interface to handle the specifics of the communications. To use this, instantiate it, then call start. When the
 * communications are finished, call requestShutdown(). Your server's protocol can call sendMessage once communication
 * is started to respond to client's messages.
 *
 * @author mhunsicker
 */
public class ProcessLauncherServer extends Server<ProcessLauncherServer.Protocol, ProcessLauncherServer.ServerObserver> {
    private volatile ExecHandle externalProcess;

    private final Logger logger = Logging.getLogger(ProcessLauncherServer.class);

    /**
     * Implement this to define the behavior of the communication on the server side.
     */
    public interface Protocol extends Server.Protocol<ProcessLauncherServer> {
        public void aboutToKillProcess();

        /**
         * fill in the information needed to execute the other process.
         *
         * @param serverPort the port the server is listening on. The client should send messages here
         * @param executionInfo an object continain information about what we execute.
         */
        public void getExecutionInfo(int serverPort, ExecutionInfo executionInfo);

        /**
         * Notification that the client has shutdown. Note: this can occur before communciations has ever started. You
         * SHOULD get this notification before receiving serverExited, even if the client fails to launch or locks up.
         *
         * @param result the return code of the client application
         * @param output the standard error and standard output of the client application
         */
        public void clientExited(int result, String output);
    }

    public interface ServerObserver extends Server.ServerObserver {
        /**
         * Notification that the client has shutdown. Note: this can occur before communciations has ever started. You
         * SHOULD get this notification before receiving serverExited, even if the client fails to launch or locks up.
         *
         * @param result the return code of the client application
         * @param output the standard error and standard output of the client application
         */
        public void clientExited(int result, String output);
    }

    public ProcessLauncherServer(Protocol protocol) {
        super(protocol);
    }

    @Override
    protected void communicationsStarted() {
        launchExternalProcess();
    }

    /**
     * This launches an external process in a thread and waits for it to exit.
     */
    private void launchExternalProcess() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                ExecutionInfo executionInfo = new ExecutionInfo();
                protocol.getExecutionInfo(getPort(), executionInfo);

                ExecHandleBuilder builder = new ExecHandleBuilder();
                builder.workingDir(executionInfo.workingDirectory);
                builder.commandLine(executionInfo.commandLineArguments);
                builder.environment(executionInfo.environmentVariables);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                builder.standardOutput(output);
                builder.errorOutput(output);
                ExecHandle execHandle = builder.build();
                setExternalProcess(execHandle);

                try {
                    execHandle.start();
                }
                catch (Throwable e) {
                    logger.error("Starting external process", e);
                    notifyClientExited( -1, e.getMessage() );
                    setExternalProcess(null);
                    return;
                }

                execHandle.waitForFinish();

                setExternalProcess(null);   //clear our external process member variable (we're using our local variable below). This is so we know the process has already stopped.

                notifyClientExited( execHandle.getExitCode(), output.toString() );
            }
        });

        thread.start();
    }

    public void stop() {
        super.stop();
        killProcess(); //if the process is still running, shut it down
    }

    public void setExternalProcess(ExecHandle externalProcess) {
        this.externalProcess = externalProcess;
    }

    /**
     * Call this to violently kill the external process. This is NOT a good way to stop it. It is preferrable to ask the
     * thread to stop. However, gradle has no way to do that, so we'll be killing it.
     */
    public synchronized void killProcess() {
        if (externalProcess != null) {
            requestShutdown();
            protocol.aboutToKillProcess();
            externalProcess.abort();
            setExternalProcess(null);
            notifyClientExited(-1, "Process Canceled");
        }
    }

    private void notifyClientExited(final int result, final String output) {
        protocol.clientExited(result, output);

        observerLord.notifyObservers(new ObserverLord.ObserverNotification<ServerObserver>() {
            public void notify(ServerObserver observer) {
                observer.clientExited(result, output);
            }
        });
    }
}