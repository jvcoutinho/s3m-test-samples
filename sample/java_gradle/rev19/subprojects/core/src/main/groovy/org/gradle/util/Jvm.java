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

package org.gradle.util;

import org.apache.tools.ant.util.JavaEnvUtils;
import org.gradle.internal.nativeplatform.OperatingSystem;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Jvm {
    private final OperatingSystem os;
    private final File javaHome;
    private final boolean strict;

    public static Jvm current() {
        return create(null);
    }

    static Jvm create(File javaHome) {
        String vendor = System.getProperty("java.vm.vendor");
        if (vendor.toLowerCase().startsWith("apple inc.")) {
            return new AppleJvm(OperatingSystem.current(), javaHome);
        }
        return new Jvm(OperatingSystem.current(), javaHome);
    }

    public Jvm(OperatingSystem os) {
        this(os, null);
    }

    Jvm(OperatingSystem os, File javaHome) {
        this.os = os;
        if (javaHome == null) {
            //discover javaHome or simply use what's in the sys. property
            File defaultHome = GFileUtils.canonicalise(new File(System.getProperty("java.home")));
            this.javaHome = discoverJavaHome(defaultHome);
            this.strict = false;
        } else {
            //precisely use what the user wants and validate when appropriate
            this.javaHome = javaHome;
            this.strict = true;
        }
    }
    
    /**
     * Creates jvm instance for given java home. Attempts to validate if provided javaHome is a valid jdk or jre location.
     *
     * @param javaHome - location of your jdk or jre (jdk is safer), cannot be null
     * @return jvm for given java home
     *
     * @throws org.gradle.util.JavaHomeException when supplied javaHome does not seem to be a valid jdk or jre location
     * @throws IllegalArgumentException when supplied javaHome is not a valid folder
     */
    public static Jvm forHome(File javaHome) throws JavaHomeException, IllegalArgumentException {
        if (javaHome == null || !javaHome.isDirectory()) {
            throw new IllegalArgumentException("Supplied javaHome must be a valid directory. You supplied: " + javaHome);
        }
        Jvm jvm = create(javaHome);
        //some validation:
        jvm.getJavaExecutable();
        return jvm;
    }

    @Override
    public String toString() {
        return String.format("%s (%s %s)", System.getProperty("java.version"), System.getProperty("java.vm.vendor"), System.getProperty("java.vm.version"));
    }

    private File getJdkExecutable(String command) {
        File executable = findExecutable(command);
        if (executable.isFile()) {
            return executable;
        }

        if (strict) {
            throw new JavaHomeException(String.format("The supplied javaHome seems to be invalid."
                    + " I cannot find the %s executable. Tried location: %s", command, executable.getAbsolutePath()));
        } else {
            //grab the executable in a backwards compatible way, via ant utility.
            return new File(JavaEnvUtils.getJdkExecutable(command));
        }
    }

    private File findExecutable(String command) {
        File exec = new File(getJavaHome(), "bin/" + command);
        return new File(os.getExecutableName(exec.getAbsolutePath()));
    }

    /**
     * @return the executable
     * @throws JavaHomeException when executable cannot be found
     */
    public File getJavaExecutable() throws JavaHomeException {
        return getJdkExecutable("java");
    }

    /**
     * @return the executable
     * @throws JavaHomeException when executable cannot be found
     */
    public File getJavadocExecutable() throws JavaHomeException {
        return getJdkExecutable("javadoc");
    }

    /**
     * @return the executable
     * @throws JavaHomeException when executable cannot be found
     */
    public File getExecutable(String name) throws JavaHomeException {
        return getJdkExecutable(name);
    }

    public boolean isJava5Compatible() {
        return System.getProperty("java.version").startsWith("1.5") || isJava6Compatible();
    }

    public boolean isJava6Compatible() {
        return System.getProperty("java.version").startsWith("1.6");
    }

    public File getJavaHome() {
        return javaHome;
    }

    private File discoverJavaHome(File candidateJavaHome) {
        File toolsJar = getToolsJar(candidateJavaHome);
        return toolsJar == null ? candidateJavaHome : toolsJar.getParentFile().getParentFile();
    }

    public File getRuntimeJar() {
        File javaHome = getJavaHome();
        File runtimeJar = new File(javaHome, "lib/rt.jar");
        return runtimeJar.exists() ? runtimeJar : null;
    }

    public File getToolsJar() {
        return getToolsJar(getJavaHome());
    }

    private File getToolsJar(File javaHome) {
        File toolsJar = new File(javaHome, "lib/tools.jar");
        if (toolsJar.exists()) {
            return toolsJar;
        }
        if (javaHome.getName().equalsIgnoreCase("jre")) {
            javaHome = javaHome.getParentFile();
            toolsJar = new File(javaHome, "lib/tools.jar");
            if (toolsJar.exists()) {
                return toolsJar;
            }
        }
        if (javaHome.getName().matches("jre\\d+") && os.isWindows()) {
            javaHome = new File(javaHome.getParentFile(), String.format("jdk%s", System.getProperty("java.version")));
            toolsJar = new File(javaHome, "lib/tools.jar");
            if (toolsJar.exists()) {
                return toolsJar;
            }
        }

        return null;
    }

    public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
        return envVars;
    }

    public boolean getSupportsAppleScript() {
        return false;
    }

    /**
     * Note: Implementation assumes that an Apple JVM always comes with a JDK rather than a JRE,
     * but this is likely an over-simplification.
     */
    public static class AppleJvm extends Jvm {
        public AppleJvm(OperatingSystem os) {
            super(os);
        }

        AppleJvm(OperatingSystem current, File javaHome) {
            super(current, javaHome);
        }

        @Override
        public File getJavaHome() {
            return super.getJavaHome();
        }

        @Override
        public File getRuntimeJar() {
            File javaHome = super.getJavaHome();
            File runtimeJar = new File(javaHome.getParentFile(), "Classes/classes.jar");
            return runtimeJar.exists() ? runtimeJar : null;
        }

        @Override
        public File getToolsJar() {
            return getRuntimeJar();
        }

        @Override
        public Map<String, ?> getInheritableEnvironmentVariables(Map<String, ?> envVars) {
            Map<String, Object> vars = new HashMap<String, Object>();
            for (Map.Entry<String, ?> entry : envVars.entrySet()) {
                if (entry.getKey().matches("APP_NAME_\\d+") || entry.getKey().matches("JAVA_MAIN_CLASS_\\d+")) {
                    continue;
                }
                vars.put(entry.getKey(), entry.getValue());
            }
            return vars;
        }

        @Override
        public boolean getSupportsAppleScript() {
            return true;
        }
    }
}
