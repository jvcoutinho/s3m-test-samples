/*******************************************************************************
 * Copyright (c) 2013 GigaSpaces Technologies Ltd. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.cloudifysource.rest.interceptors;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections.MapUtils;
import org.cloudifysource.dsl.internal.CloudifyMessageKeys;
import org.cloudifysource.dsl.rest.response.Response;
import org.cloudifysource.rest.controllers.RestErrorException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.validation.BindingResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.j_spaces.kernel.PlatformVersion;

/**
 * This intercepter has two goals.
 * <br><br>
 * 1. Validate the request is made with the current API version of the REST Gateway.
 * <br>
 * 2. Construct the {@link Response} Object after the controller has finished handling the request.
 * @author elip
 *
 */
public class ApiVersionValidationAndRestResponseBuilderInterceptor extends HandlerInterceptorAdapter {

	private static final Logger logger = Logger
			.getLogger(ApiVersionValidationAndRestResponseBuilderInterceptor.class.getName());
	
    private static final String CURRENT_API_VERSION = PlatformVersion.getVersion();

    @Autowired(required = true)
    private MessageSource messageSource;


    @Override
    public boolean preHandle(final HttpServletRequest request,
                             final HttpServletResponse response, final Object handler)
            throws Exception {

        String requestVersion = extractVersionFromRequest(request);
        if (!CURRENT_API_VERSION.equalsIgnoreCase(requestVersion)) {
            throw new RestErrorException(CloudifyMessageKeys.API_VERSION_MISMATCH.getName(),
                    requestVersion, CURRENT_API_VERSION);
        }

        return true;
    }

    private String extractVersionFromRequest(final HttpServletRequest request) {
        String requestURIWithoutContextPath =
                request.getRequestURI().substring(request.getContextPath().length()).substring(1);
        return requestURIWithoutContextPath.split("/")[0];
    }

    @Override
    public void postHandle(final HttpServletRequest request,
                           final HttpServletResponse response, final Object handler,
                           final ModelAndView modelAndView) throws Exception {
    	
        Object model = filterModel(modelAndView, handler);
        modelAndView.clear();
        response.setContentType(MediaType.APPLICATION_JSON);
        if (model instanceof Response<?>) {
            String responseBodyStr = new ObjectMapper().writeValueAsString(model);
            response.getOutputStream().write(responseBodyStr.getBytes());
            response.getOutputStream().close();

        } else {
            Response<Object> responseBodyObj = new Response<Object>();
            responseBodyObj.setResponse(model);
            responseBodyObj.setStatus("Success");
            responseBodyObj.setMessage(messageSource.getMessage(CloudifyMessageKeys.OPERATION_SUCCESSFULL.getName(),
                    new Object[] {}, Locale.US));
            responseBodyObj.setMessageId(CloudifyMessageKeys.OPERATION_SUCCESSFULL.getName());
            String responseBodyStr = new ObjectMapper().writeValueAsString(responseBodyObj);
            response.getOutputStream().write(responseBodyStr.getBytes());
            response.getOutputStream().close();
        }

    }


    /**
     * Filters the modelAndView object and retrieves the actual object returned by the controller.
     * This implementation assumes the model consists of just one returned object and a BindingResult.
     * If the model is empty, the supported return types are String (the view name) or void.
     */
    private Object filterModel(final ModelAndView modelAndView, final Object handler) 
    	throws RestErrorException {
    	
    	Object methodReturnObject = null;
    	Map<String, Object> model = modelAndView.getModel();
    	
    	if (MapUtils.isNotEmpty(model)) {
    		// the model is not empty. The return value is the first value that is not a BindingResult
    		for (Map.Entry<String, Object> entry : model.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof BindingResult)) {
                	methodReturnObject = value;
                	break;
                }
            }
    		if (methodReturnObject == null) {
    			logger.warning("return object not found in model: " + model.toString());
    			throw new RestErrorException("return object not found in model: " + model.toString());
    		}
    	} else {
    		// the model is empty, this means the return type is String or void
    		if (handler instanceof HandlerMethod) {
        		Class<?> returnType = ((HandlerMethod) handler).getMethod().getReturnType();
        		if (returnType == Void.TYPE) {
        			methodReturnObject = null;
        		} else if (returnType == String.class) {
        			String viewName = modelAndView.getViewName();
        			methodReturnObject = viewName;
        		} else {
        			logger.warning("return type not supported: " + returnType);
        			throw new RestErrorException("return type not supported: " + returnType);
        		}
        	} else {
        		logger.warning("handler object is not a HandlerMethod: " + handler);
    			throw new RestErrorException("handler object is not a HandlerMethod: " + handler);
        	}
    	}
    	
        return methodReturnObject;
    }
}
