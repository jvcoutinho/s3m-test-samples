/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.component.tool;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opengamma.OpenGammaRuntimeException;
import com.opengamma.component.ComponentManager;
import com.opengamma.financial.tool.ToolContext;
import com.opengamma.util.ArgumentChecker;

/**
 * Abstract class for command line tools.
 * <p>
 * The command line tools generally require access to key parts of the infrastructure.
 * These are provided via {@link ToolContext} which is setup and closed by this class
 * using {@link ComponentManager}. Normally the file is named {@code toolcontext.ini}.
 * 
 * @param <T> the tool context type
 */
public abstract class AbstractTool<T extends ToolContext> {

  /**
   * Logger.
   */
  private static final Logger s_logger = LoggerFactory.getLogger(AbstractTool.class);

  /**
   * Help command line option.
   */
  private static final String HELP_OPTION = "h";
  /**
   * Configuration command line option.
   */
  protected static final String CONFIG_RESOURCE_OPTION = "c";
  /**
   * Logging command line option.
   */
  private static final String LOGBACK_RESOURCE_OPTION = "l";

  /**
   * The command line.
   */
  private CommandLine _commandLine;
  /**
   * The tool contexts.
   */
  private T[] _toolContexts;

  /**
   * Initializes the tool statically.
   * 
   * @param logbackResource the logback resource location, not null
   * @return true if successful
   */
  public static final boolean init(final String logbackResource) {
    return ToolUtils.initLogback(logbackResource);
  }

  /**
   * Creates an instance.
   */
  protected AbstractTool() {
  }

  //-------------------------------------------------------------------------
  /**
   * Initializes and runs the tool from standard command-line arguments.
   * <p>
   * The base class defined three options:<br />
   * c/config - the config file, mandatory<br />
   * l/logback - the logback configuration, default tool-logback.xml<br />
   * h/help - prints the help tool<br />
   * 
   * @param args the command-line arguments, not null
   * @param toolContextClass the type of tool context to create, should match the generic type argument
   * @return true if successful, false otherwise
   */
  public boolean initAndRun(String[] args, Class<? extends T> toolContextClass) {
    return initAndRun(args, null, null, toolContextClass);
  }

  /**
   * Initializes and runs the tool from standard command-line arguments.
   * <p>
   * The base class defined three options:<br />
   * c/config - the config file, mandatory unless default specified<br />
   * l/logback - the logback configuration, default tool-logback.xml<br />
   * h/help - prints the help tool<br />
   * 
   * @param args the command-line arguments, not null
   * @param defaultConfigResource the default configuration resource location, null if mandatory on command line
   * @param defaultLogbackResource the default logback resource, null to use tool-logback.xml as the default
   * @param toolContextClass the type of tool context to create, should match the generic type argument
   * @return true if successful, false otherwise
   */
  public boolean initAndRun(String[] args, String defaultConfigResource, String defaultLogbackResource,
                            Class<? extends T> toolContextClass) {
    ArgumentChecker.notNull(args, "args");

    Options options = createOptions(defaultConfigResource == null);
    CommandLineParser parser = new PosixParser();
    CommandLine line;
    try {
      line = parser.parse(options, args);
    } catch (ParseException e) {
      usage(options);
      return false;
    }
    _commandLine = line;
    if (line.hasOption(HELP_OPTION)) {
      usage(options);
      return true;
    }
    String logbackResource = line.getOptionValue(LOGBACK_RESOURCE_OPTION);
    logbackResource = StringUtils.defaultIfEmpty(logbackResource, ToolUtils.getDefaultLogbackConfiguration());
    String[] configResources = line.getOptionValues(CONFIG_RESOURCE_OPTION);
    if (configResources == null || configResources.length == 0) {
      configResources = new String[] {defaultConfigResource};
    }
    return init(logbackResource) && run(configResources, toolContextClass);
  }

  /**
   * Runs the tool.
   * <p>
   * This starts the tool context and calls {@link #run(ToolContext)}. This will catch exceptions and print a stack trace.
   * 
   * @param configResource the config resource location, not null
   * @param toolContextClass the type of tool context to create, should match the generic type argument
   * @return true if successful
   */
  public final boolean run(String configResource, Class<? extends T> toolContextClass) {
    return run(new String[] {configResource}, toolContextClass);
  }

  /**
   * Runs the tool.
   * <p>
   * This starts the tool contexts and calls {@link #run(ToolContexts)}. This will catch exceptions and print a stack trace.
   *
   * @param configResources the config resource locations for multiple tool contexts, not null
   * @param toolContextClass the type of tool context to create, should match the generic type argument
   * @return true if successful
   */
  @SuppressWarnings("unchecked")
  public final boolean run(String[] configResources, Class<? extends T> toolContextClass) {
    try {
      ArgumentChecker.notEmpty(configResources, "configResources");
      s_logger.info("Starting " + getClass().getSimpleName());
      ToolContext[] toolContexts = new ToolContext[configResources.length];
      for (int i = 0; i < configResources.length; i++) {
        s_logger.info("Populating tool context " + (i + 1) + " of " + configResources.length + "...");
        toolContexts[i] = ToolContextUtils.getToolContext(configResources[i], toolContextClass);
      }
      s_logger.info("Running " + getClass().getSimpleName());
      run((T[]) toolContexts);
      s_logger.info("Finished " + getClass().getSimpleName());
      return true;
    } catch (Throwable ex) {
      ex.printStackTrace();
      return false;
    } finally {
      for (ToolContext toolContext : _toolContexts) {
        if (toolContext != null) {
          toolContext.close();
        }
      }
    }
  }

  /**
   * Runs the tool, calling {@code doRun}.
   * <p>
   * This will catch not handle exceptions, but will convert checked exceptions to unchecked.
   * 
   * @param toolContext the tool context, not null
   * @throws RuntimeException if an error occurs
   */
  @SuppressWarnings("unchecked")
  public final void run(T toolContext) {
    run((T[]) new ToolContext[] {toolContext});
  }

  /**
   * Runs the tool, calling {@code doRun}.
   * <p>
   * This will catch not handle exceptions, but will convert checked exceptions to unchecked.
   *
   * @param toolContexts the tool contexts, not null or empty
   * @throws RuntimeException if an error occurs
   */
  public final void run(T[] toolContexts) {
    _toolContexts = toolContexts;
    try {
      doRun();
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  //-------------------------------------------------------------------------
  /**
   * Override in subclasses to implement the tool.
   * 
   * @throws Exception if an error occurs
   */
  protected abstract void doRun() throws Exception;

  //-------------------------------------------------------------------------
  /**
   * Gets the (first) tool context.
   * 
   * @return the context, not null during {@code doRun}
   */
  protected T getToolContext() {
    return getToolContext(0);
  }

   //-------------------------------------------------------------------------
  /**
   * Gets the i-th tool context.
   *
   * @param i the index of the tool context to retrieve
   * @return the i-th context, not null during {@code doRun}
   */
  protected T getToolContext(int i) {
    ArgumentChecker.notNegative(i, "ToolContext index");
    if (getToolContexts().length > i) {
      return getToolContexts()[i];
    } else {
      throw new OpenGammaRuntimeException("ToolContext " + i + " does not exist");
    }
  }

   //-------------------------------------------------------------------------
  /**
   * Gets all tool contexts.
   *
   * @return the array of contexts, not null or empty during {@code doRun}
   */
  protected T[] getToolContexts() {
    return _toolContexts;
  }

  /**
   * Gets the parsed command line.
   * 
   * @return the parsed command line, not null after parsing
   */
  protected CommandLine getCommandLine() {
    return _commandLine;
  }

  //-------------------------------------------------------------------------
  /**
   * Creates the command line options.
   * <p>
   * Subclasses may override this and add their own parameters. The base class defined the options h/help, c/config, l/logback.
   * 
   * @param mandatoryConfigResource whether the config resource is mandatory
   * @return the set of command line options, not null
   */
  protected Options createOptions(boolean mandatoryConfigResource) {
    Options options = new Options();
    options.addOption(createHelpOption());
    options.addOption(createConfigOption(mandatoryConfigResource));
    options.addOption(createLogbackOption());
    return options;
  }

  private static Option createHelpOption() {
    return new Option(HELP_OPTION, "help", false, "prints this message");
  }

  private static Option createConfigOption(boolean mandatoryConfigResource) {
    Option option = new Option(CONFIG_RESOURCE_OPTION, "config", true, "the toolcontext configuration resource");
    option.setArgName("resource");
    option.setRequired(mandatoryConfigResource);
    return option;
  }

  private static Option createLogbackOption() {
    Option option = new Option(LOGBACK_RESOURCE_OPTION, "logback", true, "the logback configuration resource");
    option.setArgName("resource");
    option.setRequired(false);
    return option;
  }

  protected Class<?> getEntryPointClass() {
    return getClass();
  }

  protected void usage(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(120);
    formatter.printHelp("java " + getEntryPointClass().getName(), options, true);
  }

}
