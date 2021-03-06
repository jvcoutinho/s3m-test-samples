/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.request.ExecutionRequest;
import org.gradle.gradleplugin.foundation.request.RefreshTaskListRequest;
import org.gradle.gradleplugin.foundation.request.Request;
import org.gradle.gradleplugin.userinterface.swing.generic.BasicGradleUI;
import org.gradle.openapi.external.ui.AlternateUIInteractionVersion1;
import org.gradle.openapi.external.ui.BasicGradleUIVersion1;
import org.gradle.openapi.external.ui.CommandLineArgumentAlteringListenerVersion1;
import org.gradle.openapi.external.ui.GradleTabVersion1;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.gradle.openapi.external.ui.OutputObserverVersion1;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**

 @author mhunsicker
*/
public abstract class AbstractOpenAPIUIWrapper<U extends BasicGradleUI>
{
    private U basicGradleUI;
    private Map<GradleTabVersion1, GradleTabVersionWrapper> tabMap = new HashMap<GradleTabVersion1, GradleTabVersionWrapper>();
    private Map<CommandLineArgumentAlteringListenerVersion1, CommandLineArgumentAlteringListenerWrapper> commandLineListenerMap = new HashMap<CommandLineArgumentAlteringListenerVersion1, CommandLineArgumentAlteringListenerWrapper>();
    private Map<OutputObserverVersion1,OutputObserverWrapper> outputObserverMap = new HashMap<OutputObserverVersion1, OutputObserverWrapper>( );

    protected SettingsNodeVersionWrapper settingsVersionWrapper;
    protected AlternateUIInteractionVersionWrapper alternateUIInteractionVersionWrapper;

   public AbstractOpenAPIUIWrapper( SettingsNodeVersion1 settings, AlternateUIInteractionVersion1 alternateUIInteraction)
   {
      settingsVersionWrapper = new SettingsNodeVersionWrapper(settings);
      alternateUIInteractionVersionWrapper = new AlternateUIInteractionVersionWrapper(alternateUIInteraction, settingsVersionWrapper );
   }

   public void initialize( U basicGradleUI ) {

        this.basicGradleUI = basicGradleUI;
        basicGradleUI.getGradlePluginLord().addRequestObserver( new GradlePluginLord.RequestObserver()
        {
           /**
            Notification that a command is about to be executed. This is mostly useful
            for IDE's that may need to save their files.

            @param fullCommandLine the command that's about to be executed.
            @author mhunsicker
            */
           public void aboutToExecuteRequest( Request request )
           {
               alternateUIInteractionVersionWrapper.aboutToExecuteCommand( request.getFullCommandLine() );
           }

           public void executionRequestAdded( ExecutionRequest request ) { }
           public void refreshRequestAdded( RefreshTaskListRequest request ) { }
           public void requestExecutionComplete( Request request, int result, String output ) { }
        }, false );
    }

   public U getGradleUI() {
      return basicGradleUI;
   }

    /**
       Call this whenever you're about to show this panel. We'll do whatever
       initialization is necessary.
    */
    public void aboutToShow() {
        basicGradleUI.aboutToShow();
    }

    /**
     * Call this to deteremine if you can close this pane. if we're busy, we'll
     * ask the user if they want to close.
     *
     * @param closeInteraction allows us to interact with the user
     * @return true if we can close, false if not.
     */
    public boolean canClose(final BasicGradleUIVersion1.CloseInteraction closeInteraction) {
        return basicGradleUI.canClose(new BasicGradleUI.CloseInteraction() {
            public boolean promptUserToConfirmClosingWhileBusy() {
                return closeInteraction.promptUserToConfirmClosingWhileBusy();
            }
        });
    }

    /**
       Call this before you close the pane. This gives it an opportunity to do
       cleanup. You probably should call canClose before this. It gives the
       app a chance to cancel if its busy.
    */
    public void close() {
        basicGradleUI.close();
    }


   /**
      @return the root directory of your gradle project.
   */
   public File getCurrentDirectory() {
       return basicGradleUI.getGradlePluginLord().getCurrentDirectory();
   }

   /**
      @param  currentDirectory the new root directory of your gradle project.
   */
   public void setCurrentDirectory(File currentDirectory) {
       basicGradleUI.getGradlePluginLord().setCurrentDirectory(currentDirectory);
   }

   /**
    * @return the gradle home directory. Where gradle is installed.
    */
   public File getGradleHomeDirectory() {
       return basicGradleUI.getGradlePluginLord().getGradleHomeDirectory();
   }

   /**
    * This is called to get a custom gradle executable file. If you don't run
    * gradle.bat or gradle shell script to run gradle, use this to specify
    * what you do run. Note: we're going to pass it the arguments that we would
    * pass to gradle so if you don't like that, see alterCommandLineArguments.
    * Normaly, this should return null.
    *
    * @return the Executable to run gradle command or null to use the default
    */
   public File getCustomGradleExecutable() {
       return basicGradleUI.getGradlePluginLord().getCustomGradleExecutor();
   }

    /**
       Call this to add an additional tab to the gradle UI. You can call this
       at any time.
       @param  index             the index of where to add the tab.
       @param  gradleTabVersion1 the tab to add.
    */
    public void addTab(int index, GradleTabVersion1 gradleTabVersion1) {
        GradleTabVersionWrapper gradleVersionWrapper = new GradleTabVersionWrapper(gradleTabVersion1);

        //we have to store our wrapper so you can call remove tab using your passed-in object
        tabMap.put(gradleTabVersion1, gradleVersionWrapper);

        basicGradleUI.addGradleTab(index, gradleVersionWrapper);
    }

    /**
       Call this to remove one of your own tabs from this.

       @param  gradleTabVersion1 the tab to remove
    */
    public void removeTab(GradleTabVersion1 gradleTabVersion1) {
        GradleTabVersionWrapper gradleTabVersionWrapper = tabMap.remove(gradleTabVersion1);
        if (gradleTabVersionWrapper != null) {
           basicGradleUI.removeGradleTab(gradleTabVersionWrapper);
        }
    }

    public int getGradleTabCount() {
       return basicGradleUI.getGradleTabCount();
    }

    /**
       @param  index      the index of the tab
       @return the name of the tab at the specified index.
    */
    public String getGradleTabName(int index) {
        return basicGradleUI.getGradleTabName(index);
    }

    /**
     * This allows you to add a listener that can add additional command line
     * arguments whenever gradle is executed. This is useful if you've customized
     * your gradle build and need to specify, for example, an init script.
     *
     * @param listener the listener that modifies the command line arguments.
     */
    public void addCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        CommandLineArgumentAlteringListenerWrapper wrapper = new CommandLineArgumentAlteringListenerWrapper(listener);

        //we have to store our wrapper so you can call remove the listener using your passed-in object
        commandLineListenerMap.put(listener, wrapper);

        basicGradleUI.getGradlePluginLord().addCommandLineArgumentAlteringListener(wrapper);
    }

    public void removeCommandLineArgumentAlteringListener(CommandLineArgumentAlteringListenerVersion1 listener) {
        CommandLineArgumentAlteringListenerWrapper wrapper = commandLineListenerMap.remove(listener);
        if (wrapper != null) {
           basicGradleUI.getGradlePluginLord().removeCommandLineArgumentAlteringListener(wrapper);
        }
    }


    public void addOutputObserver( OutputObserverVersion1 observer )
    {
       OutputObserverWrapper wrapper = new OutputObserverWrapper( observer );
       outputObserverMap.put( observer, wrapper );

       basicGradleUI.getOutputUILord().addOutputObserver( wrapper, false );

    }

    public void removeOutputObserver( OutputObserverVersion1 observer )
    {
       OutputObserverWrapper wrapper = outputObserverMap.remove(  observer );
       if( wrapper != null ) {
          basicGradleUI.getOutputUILord().removeOutputObserver( wrapper );
       }
    }


    /**
    Call this to execute the given gradle command.

    @param commandLineArguments  the command line arguments to pass to gradle.
    @param displayName           the name displayed in the UI for this command
    */
    public void executeCommand(String commandLineArguments, String displayName) {
        //we go through the Swing version because it allows you to specify a display name
        //for the command.
        basicGradleUI.executeCommand( commandLineArguments, displayName );
    }


   /**
    This refreshes the task tree. Useful if you know you've changed something behind
    gradle's back or when first displaying this UI.
    */
   public void refreshTaskTree()
   {
      basicGradleUI.refreshTaskTree();
   }

   /**
    Determines if commands are currently being executed or not.

    @return true if we're busy, false if not.
    */
   public boolean isBusy()
   {
      return getGradleUI().isBusy();
   }

   /**
    Determines whether output is shown only when errors occur or always
    @return true to only show output if errors occur, false to show it always.
    */
   public boolean getOnlyShowOutputOnErrors()
   {
      return getGradleUI().getOutputUILord().getOnlyShowOutputOnErrors();
   }
}
