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

package com.metamatrix.cdk.api;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.metamatrix.cdk.CdkPlugin;
import com.metamatrix.cdk.IConnectorHost;
import com.metamatrix.common.application.ApplicationEnvironment;
import com.metamatrix.common.application.ApplicationService;
import com.metamatrix.common.util.PropertiesUtils;
import com.metamatrix.data.api.AsynchQueryCommandExecution;
import com.metamatrix.data.api.AsynchQueryExecution;
import com.metamatrix.data.api.Batch;
import com.metamatrix.data.api.BatchedExecution;
import com.metamatrix.data.api.BatchedUpdatesExecution;
import com.metamatrix.data.api.Connection;
import com.metamatrix.data.api.Connector;
import com.metamatrix.data.api.ConnectorCapabilities;
import com.metamatrix.data.api.ConnectorEnvironment;
import com.metamatrix.data.api.ExecutionContext;
import com.metamatrix.data.api.ProcedureExecution;
import com.metamatrix.data.api.SecurityContext;
import com.metamatrix.data.api.SynchQueryCommandExecution;
import com.metamatrix.data.api.SynchQueryExecution;
import com.metamatrix.data.api.UpdateExecution;
import com.metamatrix.data.basic.BasicBatch;
import com.metamatrix.data.exception.ConnectorException;
import com.metamatrix.data.language.ICommand;
import com.metamatrix.data.language.IDelete;
import com.metamatrix.data.language.IInsert;
import com.metamatrix.data.language.IProcedure;
import com.metamatrix.data.language.IQuery;
import com.metamatrix.data.language.IQueryCommand;
import com.metamatrix.data.language.ISetQuery;
import com.metamatrix.data.language.IUpdate;
import com.metamatrix.data.metadata.runtime.RuntimeMetadata;
import com.metamatrix.dqp.internal.datamgr.impl.ConnectorEnvironmentImpl;
import com.metamatrix.dqp.internal.datamgr.impl.ExecutionContextImpl;
import com.metamatrix.metadata.runtime.VDBMetadataFactory;

/**
 * A simple test environment to execute commands on a connector.
 * Provides an alternative to deploying the connector in the full DQP environment.
 * Can be used for testing a connector.
 */
public class ConnectorHost implements IConnectorHost {
	private static final int DEFAULT_BATCH_SIZE = 2;

    private Connector connector;
    private TranslationUtility util;
    private ConnectorEnvironment connectorEnvironment;
    private ApplicationEnvironment applicationEnvironment;
    private SecurityContext securityContext;
    private Properties connectorEnvironmentProperties;

    private List resultList;

    private int batchSize = DEFAULT_BATCH_SIZE;
    private boolean connectorStarted = false;
    
    private int batchCount = 0;
    
    /**
     * Create a new environment to test a connector.
     * @param connector a newly constructed connector to host in the new environment
     * @param connectorEnvironmentProperties the properties to expose to the connector as part of the connector environment
     * @param vdbFileName the path to the VDB file to load and use as the source of metadata for the queries sent to this connector
     */
    public ConnectorHost(Connector connector, Properties connectorEnvironmentProperties, String vdbFileName) {
        this(connector, connectorEnvironmentProperties, vdbFileName, true);
    }
    
    public ConnectorHost(Connector connector, Properties connectorEnvironmentProperties, String vdbFileName, boolean showLog) {  
        initialize(connector, connectorEnvironmentProperties, new TranslationUtility(VDBMetadataFactory.getVDBMetadata(vdbFileName)), showLog);
    }
    
    public ConnectorHost(Connector connector, Properties connectorEnvironmentProperties, TranslationUtility util) {
        initialize(connector, connectorEnvironmentProperties, util, true);
    }

    public ConnectorHost(Connector connector, Properties connectorEnvironmentProperties, TranslationUtility util, boolean showLog) {
        initialize(connector, connectorEnvironmentProperties, util, showLog);
    }
    
    private void initialize(Connector connector, Properties connectorEnvironmentProperties, TranslationUtility util, boolean showLog) {

        this.connector = connector;
        this.util = util;

        applicationEnvironment = new ApplicationEnvironment();
        connectorEnvironment = new ConnectorEnvironmentImpl(connectorEnvironmentProperties, new SysLogger(showLog), applicationEnvironment);
        this.connectorEnvironmentProperties = PropertiesUtils.clone(connectorEnvironmentProperties);
    }

    public void startConnectorIfNeeded() throws ConnectorException {
        if (!connectorStarted) {
            startConnector();
        }
    }

    private void startConnector() throws ConnectorException {
        connector.initialize(connectorEnvironment);
        connector.start();
        connectorStarted = true;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Properties getConnectorEnvironmentProperties() {
        return PropertiesUtils.clone(connectorEnvironmentProperties);
    }

    public void addResourceToConnectorEnvironment(String resourceName, Object resource) {
        applicationEnvironment.bindService(resourceName, (ApplicationService) resource);
    }

    /** 
     * @see com.metamatrix.cdk.IConnectorHost#setSecurityContext(java.lang.String, java.lang.String, java.lang.String, java.io.Serializable)
     * @since 4.2
     */
    public void setSecurityContext(String vdbName,
                                   String vdbVersion,
                                   String userName,
                                   Serializable trustedPayload) {
        setSecurityContext(vdbName, vdbVersion, userName, trustedPayload, null);
    }
    
    public void setSecurityContext(String vdbName, String vdbVersion, String userName, Serializable trustedPayload, Serializable executionPayload) {          
        this.securityContext = new ExecutionContextImpl(vdbName, vdbVersion, userName, trustedPayload, executionPayload, "Connection", "Connector<CDK>", "Request", "1", "0", false); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$  
    }
    
    public List executeCommand(String query) throws ConnectorException {
        startConnectorIfNeeded();

        Connection connection = null;
        try {
            connection = getConnection();
            ICommand command = getCommand(query);
            RuntimeMetadata runtimeMetadata = getRuntimeMetadata();

            return executeCommand(connection, command, runtimeMetadata);
        } finally {
            if (connection != null) {
                connection.release();
            }
        }
    }
    
    public List executeCommand(ICommand command) throws ConnectorException {
        startConnectorIfNeeded();

        Connection connection = null;
        try {
            connection = getConnection();
            RuntimeMetadata runtimeMetadata = getRuntimeMetadata();

            return executeCommand(connection, command, runtimeMetadata);
        } finally {
            if (connection != null) {
                connection.release();
            }
        }
    }

    private List executeCommand(Connection connection, ICommand command, RuntimeMetadata runtimeMetadata)
        throws ConnectorException {

        ExecutionContext execContext = EnvironmentUtility.createExecutionContext("100", "1"); //$NON-NLS-1$ //$NON-NLS-2$
        if(command instanceof IQuery) {
            if(connection.getCapabilities().supportsExecutionMode(ConnectorCapabilities.EXECUTION_MODE.SYNCH_QUERY)) {
                SynchQueryExecution execution = (SynchQueryExecution) connection.createExecution(ConnectorCapabilities.EXECUTION_MODE.SYNCH_QUERY, execContext, runtimeMetadata);
                execution.execute((IQuery)command, batchSize);
                readResultsFromExecution(execution);
                execution.close();                
            } else if (connection.getCapabilities().supportsExecutionMode(ConnectorCapabilities.EXECUTION_MODE.ASYNCH_QUERY)) {
                AsynchQueryExecution execution = (AsynchQueryExecution) connection.createExecution(ConnectorCapabilities.EXECUTION_MODE.ASYNCH_QUERY, execContext, runtimeMetadata);
                execution.executeAsynch((IQuery)command, batchSize);
                long pollInterval = execution.getPollInterval();
                readResultsFromAsynchExecution(execution, pollInterval);
                execution.close();                
            } else {
                executeQueryCommand(connection, (IQueryCommand)command, runtimeMetadata, execContext);
            }
        } else if (command instanceof ISetQuery) {
            executeQueryCommand(connection,(IQueryCommand) command, runtimeMetadata, execContext);
        } else if(command instanceof IInsert || command instanceof IUpdate || command instanceof IDelete) {
            UpdateExecution execution = (UpdateExecution) connection.createExecution(ConnectorCapabilities.EXECUTION_MODE.UPDATE, execContext, runtimeMetadata);

            int updated = execution.execute(command);

            Batch batch = new BasicBatch();
            batch.addRow(Arrays.asList(new Object[] { new Integer(updated) }));
            batch.setLast();

            resetResults();
            addBatchToResults(batch);

            execution.close();
        } else {
            ProcedureExecution execution = (ProcedureExecution) connection.createExecution(ConnectorCapabilities.EXECUTION_MODE.PROCEDURE, execContext, runtimeMetadata);
            execution.execute((IProcedure)command, batchSize);
            readResultsFromExecution(execution);
            execution.close();
        }

        return resultList;
    }

    private void executeQueryCommand(Connection connection,
                                     IQueryCommand command,
                                     RuntimeMetadata runtimeMetadata,
                                     ExecutionContext execContext) throws ConnectorException {
        if(connection.getCapabilities().supportsExecutionMode(ConnectorCapabilities.EXECUTION_MODE.SYNCH_QUERYCOMMAND)) {
            SynchQueryCommandExecution execution = (SynchQueryCommandExecution) connection.createExecution(ConnectorCapabilities.EXECUTION_MODE.SYNCH_QUERYCOMMAND, execContext, runtimeMetadata);
            execution.execute(command, batchSize);
            readResultsFromExecution(execution);
            execution.close();                
        } else {
            AsynchQueryCommandExecution execution = (AsynchQueryCommandExecution) connection.createExecution(ConnectorCapabilities.EXECUTION_MODE.ASYNCH_QUERYCOMMAND, execContext, runtimeMetadata);
            execution.executeAsynch(command, batchSize);
            long pollInterval = execution.getPollInterval();
            readResultsFromAsynchExecution(execution, pollInterval);
            execution.close();                
        }
    }

    public int[] executeBatchedUpdates(String[] updates) throws ConnectorException {
        startConnectorIfNeeded();

        Connection connection = null;
        try {
            connection = getConnection();
            RuntimeMetadata runtimeMetadata = getRuntimeMetadata();
            ICommand[] commands = new ICommand[updates.length];
            for (int i = 0; i < updates.length; i++) {
                commands[i] = getCommand(updates[i]);
            }

            return executeBatchedUpdates(connection, commands, runtimeMetadata);
        } finally {
            if (connection != null) {
                connection.release();
            }
        }
    }
    
    public int[] executeBatchedUpdates(Connection connection, ICommand[] commands, RuntimeMetadata runtimeMetadata) throws ConnectorException {
        BatchedUpdatesExecution execution = null;
        try {
            ExecutionContext execContext = EnvironmentUtility.createExecutionContext("100", "1"); //$NON-NLS-1$ //$NON-NLS-2$
            execution = (BatchedUpdatesExecution)connection.createExecution(ConnectorCapabilities.EXECUTION_MODE.BATCHED_UPDATES, execContext, runtimeMetadata);
            int[] counts = execution.execute(commands);
            return counts;
        } finally {
            if (execution != null) {
                execution.close();
            }
        }
    }
    
    private void readResultsFromExecution(BatchedExecution execution) throws ConnectorException {
    	Batch batch = execution.nextBatch();
        if (batch == null) {
            throw new ConnectorException(CdkPlugin.Util.getString("ConnectorHostImpl.Execution_must_return_next_batch._1")); //$NON-NLS-1$
        }
        resetResults();

        addBatchToResults(batch);
        while (!batch.isLast()) {
            batch = execution.nextBatch();
            addBatchToResults(batch);
        }
    }

    private void readResultsFromAsynchExecution(BatchedExecution execution, long pollInterval) throws ConnectorException {
        resetResults();

        boolean last = false;
        while (! last) {
            Batch batch = execution.nextBatch();
            if(batch != null) {
                addBatchToResults(batch);
                last = batch.isLast();
            }     
            
            // sleep before poll
            try {
                Thread.sleep(pollInterval);                
            } catch(InterruptedException e) {
                // ignore and cycle again
            }
        }
    }

    private void resetResults() {
    	batchCount = 0;
        resultList = new ArrayList();
    }

    private void addBatchToResults(Batch batch) {
    	batchCount++;
        resultList.addAll(Arrays.asList(batch.getResults()));
    }

    private RuntimeMetadata getRuntimeMetadata() {
        return util.createRuntimeMetadata();
    }

    public ICommand getCommand(String query) {
    	return util.parseCommand(query);
    }

    private Connection getConnection() throws ConnectorException {
        Connection connection = connector.getConnection(securityContext);
        return connection;
    }

	public int getBatchCount() {
		return batchCount;
	}
}
