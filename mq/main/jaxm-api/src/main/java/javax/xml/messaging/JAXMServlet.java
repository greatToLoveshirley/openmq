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

package javax.xml.messaging;

import javax.xml.soap.*;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.StringTokenizer;
import com.sun.messaging.jmq.resources.SharedResources;

/**
 * The superclass for components that
 * live in a servlet container that receives JAXM messages.
 * A <code>JAXMServlet</code> object is notified of a message's arrival
 * using the HTTP-SOAP binding. 
 * <P>
 * The <code>JAXMServlet</code> class is a support/utility class and is
 * provided purely as a convenience.  It is not a mandatory component, and 
 * there is no requirement that it be implemented or extended.
 * <P>
 * Note that when a component that receives messages extends
 * <code>JAXMServlet</code>, it also needs to implement either a
 * <code>ReqRespListener</code> object or a 
 * <code>OnewayListener</code> object,
 * depending on whether the component is written for a request-response
 * style of interaction or for a one-way (asynchronous) style of interaction.
 */
public abstract class JAXMServlet 
    extends HttpServlet
{
   /**
    * The <code>MessageFactory</code> object that will be used internally
    * to create the <code>SOAPMessage</code> object to be passed to the
    * method <code>onMessage</code>. This new message will contain the data
    * from the message that was posted to the servlet.  Using the 
    * <code>MessageFactory</code> object that is the value for this field 
    * to create the new message ensures that the correct profile is used.
    */
    protected MessageFactory msgFactory = null;

    private static final transient SharedResources cr = SharedResources.getResources();
 
   /**
    * Initializes this <code>JAXMServlet</code> object using the given 
    * <code>ServletConfig</code> object and initializing the
    * <code>msgFactory</code> field with a default
    * <code>MessageFactory</code> object.
    *
    * @param servletConfig the <code>ServletConfig</code> object to be
    *        used in initializing this <code>JAXMServlet</code> object
    */
    public void init(ServletConfig servletConfig) 
        throws ServletException
    {
        super.init(servletConfig);
        try {
            // Initialize it to the default.
            msgFactory = MessageFactory.newInstance();
        } catch (SOAPException ex) {
            String fctryMsg = cr.getKString(cr.X_MESSAGEFACTORY_ERROR);
            throw new ServletException(fctryMsg + "\n" + ex.getMessage());
            //throw new ServletException("Unable to create message factory"+ex.getMessage());
        }
    }
    

    /**
     * Sets this <code>JAXMServlet</code> object's <code>msgFactory</code>
     * field with the given <code>MessageFactory</code> object.
     * A <code>MessageFactory</code> object for a particular profile needs to
     * be set before a message is received in order for the message to be 
     * successfully internalized.
     *
     * @param msgFactory the <code>MessageFactory</code> object that will
     *        be used to create the <code>SOAPMessage</code> object that
     *        will be used to internalize the message that was posted to 
     *        the servlet
     */
    public void setMessageFactory(MessageFactory msgFactory) {
        this.msgFactory = msgFactory;
    }

    /**
     * Returns a <code>MimeHeaders</code> object that contains the headers
     * in the given <code>HttpServletRequest</code> object.
     *
     * @param req the <code>HttpServletRequest</code> object that a
     *        messaging provider sent to the servlet
     * @return a new <code>MimeHeaders</code> object containing the headers
     *         in the message sent to the servlet
     */
    protected static
        MimeHeaders getHeaders(HttpServletRequest req) 
    {
        Enumeration enm = req.getHeaderNames();
        MimeHeaders headers = new MimeHeaders();

        while (enm.hasMoreElements()) {
            String headerName = (String)enm.nextElement();
            String headerValue = req.getHeader(headerName);

            StringTokenizer values = new StringTokenizer(headerValue, ",");
            while (values.hasMoreTokens())
                headers.addHeader(headerName, values.nextToken().trim());
        }
        
        return headers;
    }

    /**
     * Sets the given <code>HttpServletResponse</code> object with the
     * headers in the given <code>MimeHeaders</code> object.
     * 
     * @param headers the <code>MimeHeaders</code> object containing the
     *        the headers in the message sent to the servlet
     * @param res the <code>HttpServletResponse</code> object to which the
     *        headers are to be written
     * @see #getHeaders
     */
    protected static
        void putHeaders(MimeHeaders headers, HttpServletResponse res) 
    {
        Iterator it = headers.getAllHeaders();
        while (it.hasNext()) {
            MimeHeader header = (MimeHeader)it.next();
            
            String[] values = headers.getHeader(header.getName());
            if (values.length == 1)
                res.setHeader(header.getName(), header.getValue());
            else 
                {
                    StringBuffer concat = new StringBuffer();
                    int i = 0;
                    while (i < values.length) {
                        if (i != 0)
                            concat.append(',');
                        concat.append(values[i++]);
                    }
                    
                    res.setHeader(header.getName(),
                                  concat.toString());
                }
        }
    }

    /**
     * Internalizes the given <code>HttpServletRequest</code> object
     * and writes the reply to the given <code>HttpServletResponse</code>
     * object.
     * <P>
     * Note that the value for the <code>msgFactory</code> field will be used to
     * internalize the message. This ensures that the message
     * factory for the correct profile is used.
     *
     * @param req the <code>HttpServletRequest</code> object containing the
     *        message that was sent to the servlet
     * @param resp the <code>HttpServletResponse</code> object to which the
     *        response to the message will be written
     * @throws ServletException if there is a servlet error
     * @throws IOException if there is an input or output error
     */
    public void doPost(HttpServletRequest req, 
                       HttpServletResponse resp)
        throws ServletException, IOException 
    {
	try {
            // Get all the headers from the HTTP request.
	    MimeHeaders headers = getHeaders(req);

            // Get the body of the HTTP request.
            InputStream is = req.getInputStream();

            // Now internalize the contents of a HTTP request and
            // create a SOAPMessage
	    SOAPMessage msg = msgFactory.createMessage(headers, is);
	    
	    SOAPMessage reply = null;

            // There are no replies in case of an OnewayListener.
            if (this instanceof ReqRespListener) 
                reply = ((ReqRespListener) this).onMessage(msg);
            else if (this instanceof OnewayListener)
                ((OnewayListener) this).onMessage(msg);
            else {
                String lstnrMsg = cr.getKString(cr.X_NO_JAXMSERVLET_LISTENER,
                                                this.getClass().getName());
                throw new ServletException(lstnrMsg);
                //throw new ServletException("JAXM component: "
                                           //+this.getClass().getName()+" also has to"+
                                           //" implement ReqRespListener or OnewayListener");
            }

            if (reply != null) {
                
                // Need to saveChanges 'cos we're going to use the
                // MimeHeaders to set HTTP response information. These
                // MimeHeaders are generated as part of the save.

                if (reply.saveRequired()) {
                    reply.saveChanges(); 
                }

                resp.setStatus(HttpServletResponse.SC_OK);

                putHeaders(reply.getMimeHeaders(), resp);
                    
                    // Write out the message on the response stream.
                    OutputStream os = resp.getOutputStream();
                    reply.writeTo(os);

                    os.flush();
                    
            } else 
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
        catch(Exception ex) {
            String postMsg = cr.getKString(cr.X_JAXM_POST_FAILED);
            throw new ServletException(postMsg + "\n" + ex.getMessage());
            //throw new ServletException("JAXM POST failed "+ex.getMessage());
	}
    }
}
