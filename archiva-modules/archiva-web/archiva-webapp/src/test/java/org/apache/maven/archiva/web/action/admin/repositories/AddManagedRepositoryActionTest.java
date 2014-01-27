package org.apache.maven.archiva.web.action.admin.repositories;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.opensymphony.xwork2.Action;
import org.apache.commons.io.FileUtils;
import org.apache.maven.archiva.configuration.ArchivaConfiguration;
import org.apache.maven.archiva.configuration.Configuration;
import org.apache.maven.archiva.configuration.ManagedRepositoryConfiguration;
import org.apache.maven.archiva.database.ArchivaAuditLogsDao;
import org.apache.maven.archiva.model.ArchivaAuditLogs;
import org.apache.maven.archiva.security.ArchivaRoleConstants;
import org.codehaus.plexus.redback.role.RoleManager;
import org.codehaus.redback.integration.interceptor.SecureActionBundle;
import org.codehaus.redback.integration.interceptor.SecureActionException;
import org.easymock.MockControl;

import java.util.Collections;

/**
 * AddManagedRepositoryActionTest 
 *
 * @version $Id$
 */
public class AddManagedRepositoryActionTest
    extends AbstractManagedRepositoryActionTest
{
    private AddManagedRepositoryAction action;

    private RoleManager roleManager;

    private MockControl roleManagerControl;

    private MockControl archivaConfigurationControl;

    private ArchivaConfiguration archivaConfiguration;
    
    private ArchivaAuditLogsDao auditLogsDao;

    private MockControl auditLogsDaoControl;
    
    @Override
    protected String getPlexusConfigLocation()
    {
        return AbstractManagedRepositoriesAction.class.getName().replace( '.', '/' ) + "Test.xml";
    }
    
    @Override
    protected void setUp()
        throws Exception
    {
        super.setUp();

        action = (AddManagedRepositoryAction) lookup( Action.class.getName(), "addManagedRepositoryAction" );

        archivaConfigurationControl = MockControl.createControl( ArchivaConfiguration.class );
        archivaConfiguration = (ArchivaConfiguration) archivaConfigurationControl.getMock();
        action.setArchivaConfiguration( archivaConfiguration );

        auditLogsDaoControl = MockControl.createControl( ArchivaAuditLogsDao.class );
        auditLogsDaoControl.setDefaultMatcher( MockControl.ALWAYS_MATCHER );
        auditLogsDao = (ArchivaAuditLogsDao) auditLogsDaoControl.getMock();
        action.setAuditLogsDao( auditLogsDao );
        
        roleManagerControl = MockControl.createControl( RoleManager.class );
        roleManager = (RoleManager) roleManagerControl.getMock();
        action.setRoleManager( roleManager );
        location = getTestFile( "target/test/location" );
    }

    public void testSecureActionBundle()
        throws SecureActionException
    {
        archivaConfiguration.getConfiguration();
        archivaConfigurationControl.setReturnValue( new Configuration() );
        archivaConfigurationControl.replay();

        action.prepare();
        SecureActionBundle bundle = action.getSecureActionBundle();
        assertTrue( bundle.requiresAuthentication() );
        assertEquals( 1, bundle.getAuthorizationTuples().size() );
    }

    public void testAddRepositoryInitialPage()
        throws Exception
    {
        archivaConfiguration.getConfiguration();
        archivaConfigurationControl.setReturnValue( new Configuration() );
        archivaConfigurationControl.replay();

        action.prepare();
        ManagedRepositoryConfiguration configuration = action.getRepository();
        assertNotNull( configuration );
        assertNull( configuration.getId() );
        // check all booleans are false
        assertFalse( configuration.isDeleteReleasedSnapshots() );
        assertFalse( configuration.isScanned() );
        assertFalse( configuration.isReleases() );
        assertFalse( configuration.isSnapshots() );

        String status = action.input();
        assertEquals( Action.INPUT, status );

        // check defaults
        assertFalse( configuration.isDeleteReleasedSnapshots() );
        assertTrue( configuration.isScanned() );
        assertTrue( configuration.isReleases() );
        assertFalse( configuration.isSnapshots() );
    }

    public void testAddRepository()
        throws Exception
    {
        FileUtils.deleteDirectory( location );

        roleManager.templatedRoleExists( ArchivaRoleConstants.TEMPLATE_REPOSITORY_OBSERVER, REPO_ID );
        roleManagerControl.setReturnValue( false );
        roleManager.createTemplatedRole( ArchivaRoleConstants.TEMPLATE_REPOSITORY_OBSERVER, REPO_ID );
        roleManagerControl.setVoidCallable();
        roleManager.templatedRoleExists( ArchivaRoleConstants.TEMPLATE_REPOSITORY_MANAGER, REPO_ID );
        roleManagerControl.setReturnValue( false );
        roleManager.createTemplatedRole( ArchivaRoleConstants.TEMPLATE_REPOSITORY_MANAGER, REPO_ID );
        roleManagerControl.setVoidCallable();

        roleManagerControl.replay();

        Configuration configuration = new Configuration();
        archivaConfiguration.getConfiguration();
        archivaConfigurationControl.setReturnValue( configuration );

        archivaConfiguration.save( configuration );

        archivaConfigurationControl.replay();

        action.prepare();
        ManagedRepositoryConfiguration repository = action.getRepository();
        populateRepository( repository );

        auditLogsDaoControl.expectAndReturn( auditLogsDao.saveAuditLogs( new ArchivaAuditLogs() ), null );
        auditLogsDaoControl.replay();
        
        assertFalse( location.exists() );
        String status = action.commit();
        assertEquals( Action.SUCCESS, status );
        assertTrue( location.exists() );        
        assertEquals( Collections.singletonList( repository ), configuration.getManagedRepositories() );

        roleManagerControl.verify();
        archivaConfigurationControl.verify();
        auditLogsDaoControl.verify();
    }
    
    
    public void testAddRepositoryExistingLocation()
        throws Exception
    {
        if( !location.exists() )
        {
            location.mkdirs();
        }        
    
        action.prepare();
        ManagedRepositoryConfiguration repository = action.getRepository();
        populateRepository( repository );
    
        assertTrue( location.exists() );
        String status = action.commit();
        assertEquals( AddManagedRepositoryAction.CONFIRM, status );
    }

    // TODO: test errors during add, other actions
}
