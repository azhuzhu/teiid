/*
 * JBoss, Home of Professional Open Source.
 * Copyright (C) 2008 Red Hat, Inc.
 * Copyright (C) 2000-2007 MetaMatrix, Inc.
 * Licensed to Red Hat, Inc. under one or more contributor 
 * license agreements.  See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package com.metamatrix.dqp.embedded.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import junit.framework.TestCase;

import com.metamatrix.api.exception.MetaMatrixComponentException;
import com.metamatrix.common.application.Application;
import com.metamatrix.common.config.api.ConnectorBinding;
import com.metamatrix.common.config.api.ConnectorBindingType;
import com.metamatrix.common.protocol.URLHelper;
import com.metamatrix.common.vdb.api.VDBArchive;
import com.metamatrix.core.CoreConstants;
import com.metamatrix.core.util.UnitTestUtil;
import com.metamatrix.core.vdb.VDBStatus;
import com.metamatrix.dqp.embedded.configuration.ConnectorConfigurationReader;
import com.metamatrix.dqp.embedded.configuration.VDBConfigurationReader;
import com.metamatrix.dqp.service.DQPServiceNames;


/** 
 * TestCase VDB Service
 */
public class TestEmbeddedVDBService extends TestCase{
    EmbeddedConfigurationService configService =  null;
    EmbeddedVDBService vdbService = null;
    
    protected void setUp() throws Exception {
    	System.setProperty(CoreConstants.NO_CONFIGURATION, "");//$NON-NLS-1$
        File[] files = new File(UnitTestUtil.getTestDataPath()+"/dqp/data").listFiles(); //$NON-NLS-1$
        for (int i = 0; i < files.length; i++) {
            if (!files[i].isDirectory()) {
                copy (files[i], new File(UnitTestUtil.getTestDataPath()+"/dqp/config/"+files[i].getName())); //$NON-NLS-1$
            }
        }
        Application registry = new Application();
        configService = new EmbeddedConfigurationService();
        registry.installService(DQPServiceNames.CONFIGURATION_SERVICE, configService);
        vdbService = new EmbeddedVDBService();
        registry.installService(DQPServiceNames.VDB_SERVICE, vdbService);
    }

    protected void tearDown() throws Exception {
    	vdbService.stop();
        configService.stop();
        File f = new File(UnitTestUtil.getTestDataPath()+"/dqp/config"); //$NON-NLS-1$
        if (!f.exists()) {
            f.mkdirs();
        }
        
        File[] files = f.listFiles(); 
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
        }        
    }

    void copy(File src, File target) {
        try {
            FileInputStream in = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(target);
            
            byte[] buf = new byte[1024];
            int d = in.read(buf, 0, 1024);
            while (d != -1) {
                out.write(buf, 0, d);
                d = in.read(buf, 0, 1024);            
            }            
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            // skip..
        } 
    }
    
    Properties getProperties() throws Exception{
        Properties p = new Properties();
        File f = new File(UnitTestUtil.getTestDataPath()+"/dqp/dqp.properties"); //$NON-NLS-1$
        try {            
            p.load(new FileInputStream(f)); 
        }catch(Exception e) {
            p.setProperty("vdb.definition","./config"); //$NON-NLS-1$ //$NON-NLS-2$
            p.setProperty("dqp.metadata.systemURL","../../../test.metamatrix/System.vdb"); //$NON-NLS-1$ //$NON-NLS-2$
            p.setProperty("dqp.configFile","./config/ServerConfig.xml"); //$NON-NLS-1$ //$NON-NLS-2$
            p.setProperty("dqp.classpath", "./lib"); //$NON-NLS-1$ //$NON-NLS-2$
            p.setProperty("dqp.logLevel","3"); //$NON-NLS-1$ //$NON-NLS-2$
            p.setProperty("dqp.extensions","./extensions");             //$NON-NLS-1$ //$NON-NLS-2$
        }
        p.put("dqp.propertiesFile", URLHelper.buildURL(UnitTestUtil.getTestDataPath()+"/dqp/dqp.properties")); //$NON-NLS-1$ //$NON-NLS-2$
        return p;
    }
    
    
    
    public void testGetTestVDB() throws Exception {        
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);
        
        VDBArchive vdb = vdbService.getVDB("QT_Ora9DS", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("QT_Ora9DS", vdb.getName()); //$NON-NLS-1$
        assertEquals("1", vdb.getVersion()); //$NON-NLS-1$
        
        try {
            vdb = vdbService.getVDB("Foo", "1"); //$NON-NLS-1$ //$NON-NLS-2$
            fail("must have thrown a exception"); //$NON-NLS-1$
        }catch(MetaMatrixComponentException e) {
            
        }
    }
    
    public void testSystemModelConnectorBinding() throws Exception {        
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);
        
        // asking "vdb.name" and stored "vdb.name"
        List names = vdbService.getConnectorBindingNames("QT_Ora9DS", "1", EmbeddedBaseDQPService.SYSTEM_PHYSICAL_MODEL_NAME); //$NON-NLS-1$ //$NON-NLS-2$ 
        assertEquals(1, names.size());
        assertEquals(EmbeddedBaseDQPService.SYSTEM_PHYSICAL_MODEL_NAME, (String)names.get(0)); 
    }

    // new VDB connector binding scope names
    //---------------------------------------
    //          |         stored            |
    //---------------------------------------
    // asking   |   vdb.name    |   name    |
    //---------------------------------------
    // vdb.name |   vdb.name    |trim asked |
    //---------------------------------------
    // name     | trim stored   |   name    |
    //---------------------------------------
    public void testGetConnectorBindingNames() throws Exception {
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);
        
        // asking "vdb.name" and stored "vdb.name"
        List names = vdbService.getConnectorBindingNames("QT_Ora9DS", "1", "BQT1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals(1, names.size());
        assertEquals("QT_ORA9DS_1.BQT1 Oracle 9i Simple Cap", (String)names.get(0)); //$NON-NLS-1$        
    }
    
    public void testMapConnectorBinding() throws Exception {
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);
        
        Properties props = new Properties();        
        props.setProperty("MaxResultRows","10000"); //$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("ConnectorThreadTTL", "120000");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("ConnectorMaxThreads","5");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("metamatrix.service.essentialservice", "false");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("ConnectorClassPath","extensionjar:loopbackconn.jar;extensionjar:jdbcconn.jar");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("RowCount","12");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("ServiceClassName","com.metamatrix.server.connector.service.ConnectorService");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("ConnectorClass","com.metamatrix.connector.loopback.LoopbackConnector");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("WaitTime", "0");//$NON-NLS-1$ //$NON-NLS-2$
        props.setProperty("DeployedName", "Loopy"); //$NON-NLS-1$ //$NON-NLS-2$

        ConnectorBindingType ctype = configService.getConnectorType("Loopback Connector"); //$NON-NLS-1$
        ConnectorBinding binding = ConnectorConfigurationReader.loadConnectorBinding("Loopy", props, ctype); //$NON-NLS-1$        
        List list = new ArrayList();
        list.add(binding);

        assertEquals(3, configService.getConnectorBindings().size());
        VDBArchive vdb = vdbService.getVDB("QT_Ora9DS", "1"); //$NON-NLS-1$ //$NON-NLS-2$

        assertEquals(2, vdb.getConfigurationDef().getConnectorBindings().size()); 
        configService.assignConnectorBinding("QT_Ora9DS", "1", "BQT1", (ConnectorBinding[])list.toArray(new ConnectorBinding[list.size()])); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        
        // make sure we still have two bindings
        vdb = vdbService.getVDB("QT_Ora9DS", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(2, vdb.getConfigurationDef().getConnectorBindings().size()); 
        
        // and one of them is loopy the new guy
        List names = vdbService.getConnectorBindingNames("QT_Ora9DS", "1", "BQT1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals(1, names.size());
        assertEquals("Loopy", (String)names.get(0)); //$NON-NLS-1$

        // make sure the vdb is saved with the new information by shutting down and re-starting and
        // make sure we have the new connector still
        vdbService.stopService();
        configService.stopService();
        
        Application registry = new Application();
        configService = new EmbeddedConfigurationService();
        configService.userPreferences = p;
        configService.initializeService(p);
        registry.installService(DQPServiceNames.CONFIGURATION_SERVICE, configService);
        vdbService = new EmbeddedVDBService();
        registry.installService(DQPServiceNames.VDB_SERVICE, vdbService);
        names = vdbService.getConnectorBindingNames("QT_Ora9DS", "1", "BQT1"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        assertEquals(1, names.size());
        assertEquals("Loopy", (String)names.get(0)); //$NON-NLS-1$
        configService.stopService();
    }
    
    
    public void testVDBResource() throws Exception {
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);                
        assertNotNull(vdbService.getVDBResource("Admin", "1")); //$NON-NLS-1$ //$NON-NLS-2$              
    }
    
    public void testAvailableVDBs() throws Exception {
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);                
        assertEquals(2, vdbService.getAvailableVDBs().size()); 
        
        List<VDBArchive> vdbs = vdbService.getAvailableVDBs();
        for (VDBArchive vdb:vdbs) {
            assertTrue("QT_Ora9DS|Admin".indexOf(vdb.getName()) != -1); //$NON-NLS-1$
        } // for
    }
    
    public void testDeployNewVDB() throws Exception{
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);                
        assertEquals(2, vdbService.getAvailableVDBs().size()); 
        assertEquals(3, configService.getConnectorBindings().size());
        
        File f = new File(UnitTestUtil.getTestDataPath()+"/dqp/TestEmpty.vdb"); //$NON-NLS-1$
        VDBArchive vdb = VDBConfigurationReader.loadVDB(f.toURL());
        configService.addVDB(vdb, true);
        
        assertEquals(3, vdbService.getAvailableVDBs().size()); 
        vdb = vdbService.getVDB("TestEmpty", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("TestEmpty", vdb.getName()); //$NON-NLS-1$
        assertEquals("1", vdb.getVersion()); //$NON-NLS-1$
        
        // no bindings should be added because this function only worries 
        // about the vdb not bindings. Adding is for the Data service and the admin API
        assertEquals(4, configService.getConnectorBindings().size());
    }
   
    // when we deploy the already deployed VDB it should take on the next version
    // number.
    public void testDeploySameVDB() throws Exception{
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);                
        assertEquals(2, vdbService.getAvailableVDBs().size()); 
        assertEquals(3, configService.getConnectorBindings().size());
        
        File f = new File(UnitTestUtil.getTestDataPath()+"/dqp/config/QT_Ora9DS.vdb"); //$NON-NLS-1$
        VDBArchive vdb = VDBConfigurationReader.loadVDB(f.toURL());
        configService.addVDB(vdb, true);
        
        assertEquals(3, vdbService.getAvailableVDBs().size()); 
        vdb = vdbService.getVDB("QT_Ora9DS", "2"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("QT_Ora9DS", vdb.getName()); //$NON-NLS-1$
        assertEquals("2", vdb.getVersion()); //$NON-NLS-1$
        
        // one of the binding is shared.
        assertEquals(3, configService.getConnectorBindings().size());
    }
    
    
    public void changeVDBStatus_delete() throws Exception {
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);                
        assertEquals(2, vdbService.getAvailableVDBs().size()); 
        
        vdbService.changeVDBStatus("QT_Ora9DS", "1", VDBStatus.DELETED); //$NON-NLS-1$ //$NON-NLS-2$
        
        assertEquals(1, vdbService.getAvailableVDBs().size()); 
    }

    public void changeVDBStatus_inactive() throws Exception {
        Properties p = getProperties();        
        configService.userPreferences = p;
        configService.initializeService(p);                
        assertEquals(2, vdbService.getAvailableVDBs().size()); 
        
        vdbService.changeVDBStatus("QT_Ora9DS", "1", VDBStatus.INACTIVE); //$NON-NLS-1$ //$NON-NLS-2$
        
        assertEquals(2, vdbService.getAvailableVDBs().size()); 
        VDBArchive vdb = vdbService.getVDB("QT_Ora9DS", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(VDBStatus.INACTIVE, vdb.getStatus());
    }    
}
