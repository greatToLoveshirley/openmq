/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2000-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/*
 * @(#)MQAuthenticator.java	1.14 06/28/07
 */ 

package com.sun.messaging.jmq.jmsserver.auth;

import java.util.List;
import java.util.Hashtable;
import java.util.Properties;
import java.security.AccessControlException;
import javax.security.auth.Subject;
import javax.security.auth.Refreshable;
import javax.security.auth.login.LoginException;
import com.sun.messaging.jmq.auth.api.FailedLoginException;
import com.sun.messaging.jmq.util.ServiceType;
import com.sun.messaging.jmq.util.log.Logger;
import com.sun.messaging.jmq.jmsserver.Globals;
import com.sun.messaging.jmq.jmsserver.resources.BrokerResources;
import com.sun.messaging.jmq.jmsserver.util.BrokerException;
import com.sun.messaging.jmq.jmsserver.config.BrokerConfig;
import com.sun.messaging.jmq.jmsserver.service.ServiceManager;
import com.sun.messaging.jmq.auth.api.server.*;

/**
 * This class is to be used to shut-circus client connection authentication.
 * An object of this class should only be used for 1 connection
 */

public class MQAuthenticator {

    private static boolean DEBUG = false; 

    private String serviceName = null;
    //private int serviceType;
    private String serviceTypeStr = null;

    private Hashtable handlers = new Hashtable();
    private AuthCacheData authCacheData = new AuthCacheData();
    private AccessController ac = null;

    public MQAuthenticator(String serviceName, int serviceType) 
                                      throws BrokerException {

        this.serviceName = serviceName;
        //this.serviceType = serviceType;
        this.serviceTypeStr = ServiceType.getServiceTypeString(serviceType);
        this.ac = AccessController.getInstance(serviceName, serviceType); 
    }

    public void authenticate(String username, String password) 
           throws BrokerException, LoginException, AccessControlException {
        authenticate(username, password, true);
    }

    public void authenticate(String username, String password, boolean logout) 
           throws BrokerException, LoginException, AccessControlException {

        String authType = ac.getAuthType();
        com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler hd
                                               = getClientAuthHandler(authType);
        if (!hd.getType().equals(authType)) {
            String[] args = {authType, hd.getType(), hd.getClass().getName()};
            throw new BrokerException(Globals.getBrokerResources().getKString(
                                    BrokerResources.X_AUTHTYPE_MISMATCH, args));
        }
        hd.init(username, password, null /* props */);

        int seq = 0;
        byte[] req = ac.getChallenge(seq, 
                                     new Properties(), 
                                     getAuthCacheData().getCacheData(), 
                                     null /* overrideType */);
        do {
            req = ac.handleResponse(hd.handleRequest(req, seq++), seq);
        } while (req != null);
        authCacheData.setCacheData(ac.getCacheData());

        ac.checkConnectionPermission(serviceName, serviceTypeStr); 

        //Subject sj = ac.getAuthenticatedSubject();
	if (logout)  {
            ac.logout();
	}
    }

    public void logout()  {
	if (ac != null)  {
            ac.logout();
	}
    }

    public AuthCacheData getAuthCacheData() {
        return authCacheData;
    }

    public AccessController getAccessController() {
        return ac;
    }

    private com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler 
                                           getClientAuthHandler(String authType) 
                                                         throws BrokerException {

        com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler hd =
           (com.sun.messaging.jmq.auth.api.client.AuthenticationProtocolHandler)
                                                          handlers.get(authType);
        if (hd != null) return hd;

        if (authType.equals(AccessController.AUTHTYPE_BASIC)) {
            hd = new com.sun.messaging.jmq.auth.handlers.BasicAuthenticationHandler();
            handlers.put(authType, hd);
        } else if (authType.equals(AccessController.AUTHTYPE_DIGEST)) {
            hd = new com.sun.messaging.jmq.auth.handlers.DigestAuthenticationHandler();
            handlers.put(authType, hd);
        } else {

            /* 
             * Currently not supported, client side uses the following property to
             * indicate a plugin
             *
             * "JMQAuthClass" + "." + authType
             */
        
            throw new BrokerException(Globals.getBrokerResources().getKString(
                         BrokerResources.X_UNSUPPORTED_AUTHTYPE, authType));
        }
        return hd;
    }

    public static final String CMDUSER_PROPERTY = Globals.IMQ + ".imqcmd.user";
    public static final String CMDUSER_PWD_PROPERTY = Globals.IMQ + ".imqcmd.password";
    public static final String CMDUSER_SVC_PROPERTY = Globals.IMQ + ".imqcmd.service";

    public static boolean authenticateCMDUserIfset() {
        BrokerConfig bcfg = Globals.getConfig();
        String cmduser = bcfg.getProperty(CMDUSER_PROPERTY);
        if (cmduser == null)  return true;

        Logger logger = Globals.getLogger();
        BrokerResources rb = Globals.getBrokerResources();
        if (cmduser.trim().length() == 0) {
            logger.log(Logger.FORCE, rb.X_BAD_PROPERTY_VALUE,
                                     CMDUSER_PROPERTY+ "=" + cmduser);
            return false;
        }
            /*
            if (!bcfg.getBooleanProperty(Globals.KEYSTORE_USE_PASSFILE_PROP)) {
                logger.log(Logger.FORCE, rb.E_AUTH_CMDUSER_PASSFILE_NOT_ENABLED,
                           Globals.KEYSTORE_USE_PASSFILE_PROP, cmduserProp);
                return false;
            }
            */
        String cmdpwd = bcfg.getProperty(CMDUSER_PWD_PROPERTY);
        if (cmdpwd == null) {
            logger.log(Logger.FORCE, rb.X_PASSWORD_NOT_PROVIDED,
                                     CMDUSER_PROPERTY+"="+cmduser);
            return false;
        }
        String cmdsvc = bcfg.getProperty(CMDUSER_SVC_PROPERTY);
        if (cmdsvc == null) cmdsvc = "admin";
        List activesvcs= ServiceManager.getAllActiveServiceNames();
        if (activesvcs == null || !activesvcs.contains(cmdsvc)) {
            logger.log(Logger.FORCE, rb.E_NOT_ACTIVE_SERVICE, cmdsvc, 
                                     CMDUSER_PROPERTY+"="+cmduser);
            return false;
        }
        String str = ServiceManager.getServiceTypeString(cmdsvc);
        if (str == null || ServiceType.getServiceType(str) != ServiceType.ADMIN) {
            String args[] = {cmdsvc, str, CMDUSER_PROPERTY+"="+cmduser};
            logger.log(Logger.FORCE, rb.E_NOT_ADMIN_SERVICE, args);
            return false;
        }
        try {
            MQAuthenticator a = new MQAuthenticator(cmdsvc, ServiceType.ADMIN);
            a.authenticate(cmduser, cmdpwd);
            if (DEBUG) {
            logger.log(Logger.FORCE, rb.I_AUTH_OK, 
                       CMDUSER_PROPERTY+"="+cmduser, cmdsvc);
            }
            return true;
        } catch (Exception e) {
            if (DEBUG) {
            logger.logStack(Logger.FORCE, rb.W_AUTH_FAILED, cmduser, cmdsvc, e);
            } else {
            logger.log(Logger.FORCE, rb.W_AUTH_FAILED, cmduser, cmdsvc);
            }
            return false; 
        }
    }
}
