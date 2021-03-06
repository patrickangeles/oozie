/**
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. See accompanying LICENSE file.
 */
package org.apache.oozie.action.email;

import org.apache.oozie.WorkflowActionBean;
import org.apache.oozie.WorkflowJobBean;
import org.apache.oozie.action.hadoop.ActionExecutorTestCase;
import org.apache.oozie.service.WorkflowAppService;
import org.apache.oozie.util.XConfiguration;
import org.apache.oozie.util.XmlUtils;
import org.jdom.Element;
import org.jdom.JDOMException;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;

public class TestEmailActionExecutor extends ActionExecutorTestCase {

    // GreenMail helps unit test with functional in-memory mail servers.
    GreenMail server;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new GreenMail();
        server.start();
    }

    @Override
    protected void setSystemProps() {
        super.setSystemProps();
        setSystemProperty("oozie.service.ActionService.executor.classes", EmailActionExecutor.class.getName());
    }

    private Context createNormalContext(String actionXml) throws Exception {
        EmailActionExecutor ae = new EmailActionExecutor();

        XConfiguration protoConf = new XConfiguration();
        protoConf.set(WorkflowAppService.HADOOP_USER, getTestUser());
        protoConf.set(WorkflowAppService.HADOOP_UGI, getTestUser() + "," + getTestGroup());
        protoConf.setInt("oozie.email.smtp.port", server.getSmtp().getPort());
        protoConf.set("oozie.email.smtp.host", "localhost");
        protoConf.set("oozie.email.from.address", "test@oozie.com");
        injectKerberosInfo(protoConf);

        WorkflowJobBean wf = createBaseWorkflow(protoConf, "email-action");
        WorkflowActionBean action = (WorkflowActionBean) wf.getActions().get(0);
        action.setType(ae.getType());
        action.setConf(actionXml);

        return new Context(wf, action);
    }

    private Context createAuthContext(String actionXml) throws Exception {
        Context ctx = createNormalContext(actionXml);
        ctx.getProtoActionConf().setBoolean("oozie.email.smtp.auth", true);
        ctx.getProtoActionConf().set("oozie.email.smtp.username", "test@oozie.com");
        ctx.getProtoActionConf().set("oozie.email.smtp.password", "oozie");
        return ctx;
    }

    private Element prepareEmailElement(Boolean ccs) throws JDOMException {
        StringBuilder elem = new StringBuilder();
        elem.append("<email xmlns=\"uri:oozie:email-action:0.1\">");
        elem.append("<to>    abc@oozie.com, def@oozie.com    </to>");
        if (ccs) {
            elem.append("<cc>ghi@oozie.com,jkl@oozie.com</cc>");
        }
        elem.append("<subject>sub</subject>");
        elem.append("<body>bod</body>");
        elem.append("</email>");
        return XmlUtils.parseXml(elem.toString());
    }

    private Element prepareBadElement(String elem) throws JDOMException {
        Element good = prepareEmailElement(true);
        good.getChild("email").addContent(new Element(elem));
        return good;
    }

    public void testSetupMethods() {
        EmailActionExecutor email = new EmailActionExecutor();
        assertEquals("email", email.getType());
    }

    public void testDoNormalEmail() throws Exception {
        EmailActionExecutor email = new EmailActionExecutor();
        email.validateAndMail(createNormalContext("email-action"), prepareEmailElement(false));
        assertEquals("bod", GreenMailUtil.getBody(server.getReceivedMessages()[0]));
    }

    public void testDoAuthEmail() throws Exception {
        EmailActionExecutor email = new EmailActionExecutor();
        email.validateAndMail(createAuthContext("email-action"), prepareEmailElement(true));
        assertEquals("bod", GreenMailUtil.getBody(server.getReceivedMessages()[0]));
    }

    public void testValidation() throws Exception {
        EmailActionExecutor email = new EmailActionExecutor();

        Context ctx = createNormalContext("email-action");

        // Multiple <to>s
        try {
            email.validateAndMail(ctx, prepareBadElement("to"));
            fail();
        } catch (Exception e) {
            // Validation succeeded.
        }

        // Multiple <cc>s
        try {
            email.validateAndMail(ctx, prepareBadElement("cc"));
            fail();
        } catch (Exception e) {
            // Validation succeeded.
        }

        // Multiple <subject>s
        try {
            email.validateAndMail(ctx, prepareBadElement("subject"));
            fail();
        } catch (Exception e) {
            // Validation succeeded.
        }

        // Multiple <body>s
        try {
            email.validateAndMail(ctx, prepareBadElement("body"));
            fail();
        } catch (Exception e) {
            // Validation succeeded.
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        server.stop();
    }
}
