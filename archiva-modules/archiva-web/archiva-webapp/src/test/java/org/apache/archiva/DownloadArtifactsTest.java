package org.apache.archiva;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.archiva.admin.model.beans.RemoteRepository;
import org.apache.archiva.security.common.ArchivaRoleConstants;
import org.apache.commons.io.FileUtils;
import org.apache.maven.wagon.providers.http.HttpWagon;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.redback.rest.api.services.RoleManagementService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Olivier Lamy
 */
@RunWith( JUnit4.class )
public class DownloadArtifactsTest
    extends AbstractDownloadTest
{

    protected Logger log = LoggerFactory.getLogger( DownloadArtifactsTest.class );

    public Server redirectServer = null;

    public int redirectPort;

    @BeforeClass
    public static void setAppServerBase()
    {
        previousAppServerBase = System.getProperty( "appserver.base" );
        System.setProperty( "appserver.base", "target/" + DownloadArtifactsTest.class.getName() );
    }


    @AfterClass
    public static void resetAppServerBase()
    {
        System.setProperty( "appserver.base", previousAppServerBase );
    }

    @Before
    public void startServer()
        throws Exception
    {
        super.startServer();

        //redirect handler

        this.redirectServer = new Server( 0 );
        ServletHolder shRedirect = new ServletHolder( getServletClass() );
        ServletContextHandler contextRedirect = new ServletContextHandler();

        contextRedirect.setContextPath( "/" );
        contextRedirect.addServlet( shRedirect, "/*" );

        redirectServer.setHandler( contextRedirect );
        redirectServer.start();
        this.redirectPort = redirectServer.getConnectors()[0].getLocalPort();
        log.info( "redirect server port {}", redirectPort );
    }

    @After
    public void tearDown()
        throws Exception
    {
        super.tearDown();
        if ( this.redirectServer != null )
        {
            this.redirectServer.stop();
        }
    }

    @Test
    public void downloadWithRemoteRedirect()
        throws Exception
    {
        RemoteRepository remoteRepository = getRemoteRepositoriesService().getRemoteRepository( "central" );
        remoteRepository.setUrl( "http://localhost:" + redirectPort );
        getRemoteRepositoriesService().updateRemoteRepository( remoteRepository );

        RoleManagementService roleManagementService = getRoleManagementService( authorizationHeader );

        if ( !roleManagementService.templatedRoleExists( ArchivaRoleConstants.TEMPLATE_REPOSITORY_OBSERVER,
                                                         "internal" ) )
        {
            roleManagementService.createTemplatedRole( ArchivaRoleConstants.TEMPLATE_REPOSITORY_OBSERVER, "internal" );
        }

        getUserService( authorizationHeader ).createGuestUser();
        roleManagementService.assignRole( ArchivaRoleConstants.TEMPLATE_GUEST, "guest" );

        roleManagementService.assignTemplatedRole( ArchivaRoleConstants.TEMPLATE_REPOSITORY_OBSERVER, "internal",
                                                   "guest" );

        getUserService( authorizationHeader ).removeFromCache( "guest" );

        File file = new File( "target/junit-4.9.jar" );
        if ( file.exists() )
        {
            file.delete();
        }

        HttpWagon httpWagon = new HttpWagon();
        httpWagon.connect( new Repository( "foo", "http://localhost:" + port ) );

        httpWagon.get( "/repository/internal/junit/junit/4.9/junit-4.9.jar", file );

        ZipFile zipFile = new ZipFile( file );
        List<String> entries = getZipEntriesNames( zipFile );
        ZipEntry zipEntry = zipFile.getEntry( "org/junit/runners/JUnit4.class" );
        assertNotNull( "cannot find zipEntry org/junit/runners/JUnit4.class, entries: " + entries + ", content is: "
                           + FileUtils.readFileToString( file ), zipEntry );
        zipFile.close();
        file.deleteOnExit();
    }

    private List<String> getZipEntriesNames( ZipFile zipFile )
    {
        try
        {
            List<String> entriesNames = new ArrayList<String>();
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while ( entries.hasMoreElements() )
            {
                entriesNames.add( entries.nextElement().getName() );
            }
            return entriesNames;
        }
        catch ( Throwable e )
        {
            log.info( "fail to get zipEntries " + e.getMessage(), e );
        }
        return Collections.emptyList();
    }

    @Override
    protected Class getServletClass()
    {
        return RedirectServlet.class;
    }

    public static class RedirectServlet
        extends HttpServlet
    {
        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse resp )
            throws ServletException, IOException
        {

            LoggerFactory.getLogger( getClass() ).info( "redirect servlet receive: {}", req.getRequestURI() );
            resp.setStatus( 302 );
            resp.getWriter().write( "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n" + "<html><head>\n"
                                        + "<title>302 Found</title>\n" + "</head><body>\n" + "<h1>Found</h1>\n"
                                        + "<p>The document has moved <a href=\"http://repo1.maven.apache.org/maven2/junit/junit/4.9/junit-4.9.jar\">here</a>.</p>\n"
                                        + "</body></html>\n" + "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n"
                                        + "<html><head>\n" );
            resp.sendRedirect( "http://repo1.maven.apache.org/maven2/" + req.getRequestURI() );
        }
    }


}
