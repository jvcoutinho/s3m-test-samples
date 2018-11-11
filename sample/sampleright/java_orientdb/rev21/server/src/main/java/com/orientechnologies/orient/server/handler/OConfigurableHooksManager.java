package com.orientechnologies.orient.server.handler;

import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseComplex;
import com.orientechnologies.orient.core.db.ODatabaseInternal;
import com.orientechnologies.orient.core.db.ODatabaseLifecycleListener;
import com.orientechnologies.orient.core.hook.ORecordHook;
import com.orientechnologies.orient.server.config.OServerConfiguration;
import com.orientechnologies.orient.server.config.OServerHookConfiguration;
import com.orientechnologies.orient.server.config.OServerParameterConfiguration;

import java.lang.reflect.Method;
import java.util.List;

/**
 * User: kasper fock Date: 09/11/12 Time: 22:35 Registers hooks defined the in xml configuration.
 * 
 * Hooks can be defined in xml as :
 * 
 * <hooks> <hook class="HookClass"> <parameters> <parameter name="foo" value="bar" /> </parameters> </hook> </hooks> In case any
 * parameters is defined the hook class should have a method with following signature: public void config(OServer oServer,
 * OServerParameterConfiguration[] iParams)
 */
public class OConfigurableHooksManager implements ODatabaseLifecycleListener {

  private List<OServerHookConfiguration> configuredHooks;

  public OConfigurableHooksManager(final OServerConfiguration iCfg) {
    configuredHooks = iCfg.hooks;
    if (configuredHooks != null && !configuredHooks.isEmpty())
      Orient.instance().addDbLifecycleListener(this);
  }

  @Override
  public PRIORITY getPriority() {
    return PRIORITY.LAST;
  }

  @Override
  public void onCreate(final ODatabaseInternal iDatabase) {
    onOpen(iDatabase);
  }

  public void onOpen(ODatabaseInternal iDatabase) {
    if (iDatabase instanceof ODatabaseComplex) {
      final ODatabaseComplex<?> db = (ODatabaseComplex<?>) iDatabase;
      for (OServerHookConfiguration hook : configuredHooks) {
        try {
          final ORecordHook.HOOK_POSITION pos = ORecordHook.HOOK_POSITION.valueOf(hook.position);
          final ORecordHook h = (ORecordHook) Class.forName(hook.clazz).newInstance();
          if (hook.parameters != null && hook.parameters.length > 0)
            try {
              final Method m = h.getClass().getDeclaredMethod("config", new Class[] { OServerParameterConfiguration[].class });
              m.invoke(h, new Object[] { hook.parameters });
            } catch (Exception e) {
              OLogManager
                  .instance()
                  .warn(
                      this,
                      "[configure] Failed to configure hook '%s'. Parameters specified but hook don support parameters. Should have a method config with parameters OServerParameterConfiguration[] ",
                      hook.clazz);

            }
          db.registerHook(h, pos);
        } catch (Exception e) {
          OLogManager.instance().error(this, "[configure] Failed to configure hook '%s' due to the an error : ", e, hook.clazz,
              e.getMessage());
        }
      }
    }

  }

  public void onClose(ODatabaseInternal iDatabase) {
  }

  public String getName() {
    return "HookRegisters";
  }
}
