/*
 * OpenBench LogicSniffer / SUMP project 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *
 * 
 * Copyright (C) 2010-2011 - J.W. Janssen, http://www.lxtreme.nl
 */
package nl.lxtreme.ols.client;


import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.event.*;

import nl.lxtreme.ols.api.*;
import nl.lxtreme.ols.api.acquisition.*;
import nl.lxtreme.ols.api.data.*;
import nl.lxtreme.ols.api.data.export.*;
import nl.lxtreme.ols.api.data.project.*;
import nl.lxtreme.ols.api.devices.*;
import nl.lxtreme.ols.api.tools.*;
import nl.lxtreme.ols.api.ui.*;
import nl.lxtreme.ols.client.action.*;
import nl.lxtreme.ols.client.action.manager.*;
import nl.lxtreme.ols.client.diagram.*;
import nl.lxtreme.ols.client.diagram.settings.*;
import nl.lxtreme.ols.client.osgi.*;
import nl.lxtreme.ols.util.*;

import org.osgi.framework.*;


/**
 * Denotes a front-end controller for the client.
 */
public final class ClientController implements ActionProvider, AcquisitionProgressListener, AcquisitionStatusListener,
    AcquisitionDataListener, AnalysisCallback, IClientController
{
  // INNER TYPES

  /**
   * Provides a default tool context implementation.
   */
  static final class DefaultToolContext implements ToolContext
  {
    // VARIABLES

    private final int startSampleIdx;
    private final int endSampleIdx;
    private final int channels;
    private final int enabledChannels;

    // CONSTRUCTORS

    /**
     * Creates a new DefaultToolContext instance.
     * 
     * @param aStartSampleIdx
     *          the starting sample index;
     * @param aEndSampleIdx
     *          the ending sample index;
     * @param aChannels
     *          the available channels in the acquisition result;
     * @param aEnabledChannels
     *          the enabled channels in the acquisition result.
     */
    public DefaultToolContext( final int aStartSampleIdx, final int aEndSampleIdx, final int aChannels,
        final int aEnabledChannels )
    {
      this.startSampleIdx = aStartSampleIdx;
      this.endSampleIdx = aEndSampleIdx;
      this.channels = aChannels;
      this.enabledChannels = aEnabledChannels;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getChannels()
    {
      return this.channels;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEnabledChannels()
    {
      return this.enabledChannels;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getEndSampleIndex()
    {
      return this.endSampleIdx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLength()
    {
      return Math.max( 0, this.endSampleIdx - this.startSampleIdx );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStartSampleIndex()
    {
      return this.startSampleIdx;
    }
  }

  // CONSTANTS

  private static final Logger LOG = Logger.getLogger( ClientController.class.getName() );

  // VARIABLES

  private final OsgiHelper osgiHelper;

  private final BundleContext bundleContext;
  private final IActionManager actionManager;

  private DataContainer dataContainer;
  private ProjectManager projectManager;
  private DataAcquisitionService dataAcquisitionService;

  private final EventListenerList evenListeners;

  private volatile MainFrame mainFrame;
  private volatile String selectedDevice;

  // CONSTRUCTORS

  /**
   * Creates a new ClientController instance.
   * 
   * @param aBundleContext
   *          the bundle context to use for interaction with the OSGi framework;
   * @param aHost
   *          the current host to use, cannot be <code>null</code>;
   * @param aProjectManager
   *          the project manager to use, cannot be <code>null</code>.
   */
  public ClientController( final BundleContext aBundleContext )
  {
    this.bundleContext = aBundleContext;

    this.osgiHelper = new OsgiHelper( aBundleContext );

    this.evenListeners = new EventListenerList();
    this.actionManager = ActionManagerFactory.createActionManager( this );
  }

  // METHODS

  /**
   * {@inheritDoc}
   */
  @Override
  public void acquisitionComplete( final AcquisitionResult aData )
  {
    setAcquisitionResult( aData );

    updateActionsOnEDT();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void acquisitionEnded( final AcquisitionResultStatus aStatus )
  {
    if ( aStatus.isAborted() )
    {
      setStatus( "Capture aborted! " + aStatus.getMessage() );
    }
    else if ( aStatus.isFailed() )
    {
      setStatus( "Capture failed! " + aStatus.getMessage() );
    }
    else
    {
      setStatus( "Capture finished at {0,date,medium} {0,time,medium}.", new Date() );
    }

    updateActionsOnEDT();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void acquisitionInProgress( final int aPercentage )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.setProgress( aPercentage );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void acquisitionStarted()
  {
    updateActionsOnEDT();
  }

  /**
   * Adds a cursor change listener.
   * 
   * @param aListener
   *          the listener to add, cannot be <code>null</code>.
   */
  public void addCursorChangeListener( final DiagramCursorChangeListener aListener )
  {
    this.evenListeners.add( DiagramCursorChangeListener.class, aListener );
  }

  /**
   * Adds the menu component of the given provider to this controller, and does
   * this synchronously on the EDT.
   * 
   * @param aProvider
   *          the menu component provider, cannot be <code>null</code>.
   */
  public void addMenu( final ComponentProvider aProvider )
  {
    if ( this.mainFrame != null )
    {
      SwingUtilities.invokeLater( new Runnable()
      {
        @Override
        public void run()
        {
          final JMenu menu = ( JMenu )aProvider.getComponent();
          final JMenuBar menuBar = ClientController.this.mainFrame.getJMenuBar();
          menuBar.add( menu );
          aProvider.addedToContainer();

          menuBar.revalidate();
          menuBar.repaint();
        }
      } );
    }
  }

  /**
   * @see nl.lxtreme.ols.api.tools.AnalysisCallback#analysisAborted(java.lang.String)
   */
  @Override
  public void analysisAborted( final String aReason )
  {
    setStatus( "Analysis aborted! " + aReason );

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void analysisComplete( final AcquisitionResult aNewData )
  {
    if ( aNewData != null )
    {
      this.dataContainer.setCapturedData( aNewData );
    }
    if ( this.mainFrame != null )
    {
      repaintMainFrame();
    }

    setStatus( "" );
    updateActions();
  }

  /**
   * @see nl.lxtreme.ols.client.IClientController#cancelCapture()
   */
  public void cancelCapture()
  {
    final DataAcquisitionService acquisitionService = getDataAcquisitionService();
    if ( acquisitionService != null )
    {
      acquisitionService.cancelAcquisition();
    }

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public boolean captureData( final Window aParent )
  {
    final DataAcquisitionService acquisitionService = getDataAcquisitionService();
    final DeviceController devCtrl = getDeviceController();
    if ( ( devCtrl == null ) || ( acquisitionService == null ) )
    {
      return false;
    }

    try
    {
      if ( devCtrl.setupCapture( aParent ) )
      {
        setStatus( "Capture from {0} started at {1,date,medium} {1,time,medium} ...", devCtrl.getName(), new Date() );

        acquisitionService.acquireData( devCtrl );
        return true;
      }

      return false;
    }
    catch ( IOException exception )
    {
      setStatus( "I/O problem: " + exception.getMessage() );

      // Make sure to handle IO-interrupted exceptions properly!
      if ( !HostUtils.handleInterruptedException( exception ) )
      {
        exception.printStackTrace();
      }

      return false;
    }
    finally
    {
      updateActions();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void clearAllCursors()
  {
    for ( int i = 0; i < Ols.MAX_CURSORS; i++ )
    {
      this.dataContainer.setCursorPosition( i, null );
    }
    fireCursorChangedEvent( 0, -1 ); // removed...

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public void createNewProject()
  {
    this.projectManager.createNewProject();

    if ( this.mainFrame != null )
    {
      this.mainFrame.repaint();
    }

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public void exit()
  {
    try
    {
      // Stop the framework bundle; which should stop all other bundles as
      // well; the STOP_TRANSIENT option ensures the bundle is restarted the
      // next time...
      this.bundleContext.getBundle( 0 ).stop( Bundle.STOP_TRANSIENT );
    }
    catch ( IllegalStateException ex )
    {
      LOG.warning( "Bundle context no longer valid while shutting down client?!" );

      // The bundle context is no longer valid; we're going to exit anyway, so
      // lets ignore this exception for now...
      System.exit( -1 );
    }
    catch ( BundleException be )
    {
      LOG.warning( "Bundle context no longer valid while shutting down client?!" );

      System.exit( -1 );
    }
  }

  /**
   * {@inheritDoc}
   */
  public void exportTo( final String aExporterName, final File aExportFile ) throws IOException
  {
    if ( this.mainFrame == null )
    {
      return;
    }

    OutputStream writer = null;

    try
    {
      writer = new FileOutputStream( aExportFile );

      final Exporter exporter = getExporter( aExporterName );
      exporter.export( this.dataContainer, this.mainFrame.getDiagramScrollPane(), writer );

      setStatus( "Export to {0} succesful ...", aExporterName );
    }
    finally
    {
      HostUtils.closeResource( writer );
    }
  }

  /**
   * @see nl.lxtreme.ols.client.ActionProvider#getAction(java.lang.String)
   */
  public Action getAction( final String aID )
  {
    return this.actionManager.getAction( aID );
  }

  /**
   * {@inheritDoc}
   */
  public DataContainer getDataContainer()
  {
    return this.dataContainer;
  }

  /**
   * Returns the current device controller.
   * 
   * @return the current device controller, can be <code>null</code>.
   */
  public final DeviceController getDeviceController()
  {
    if ( this.selectedDevice != null )
    {
      return getDeviceController( this.selectedDevice );
    }
    return null;
  }

  /**
   * Returns all available device controllers.
   * 
   * @return an array of device controller names, never <code>null</code>, but
   *         an empty array is possible.
   */
  public String[] getDeviceNames()
  {
    return getAllServiceNamesFor( DeviceController.class );
  }

  /**
   * Returns the current diagram settings.
   * 
   * @return the current diagram settings, can be <code>null</code> if there is
   *         no main frame to take the settings from.
   */
  public final DiagramSettings getDiagramSettings()
  {
    final Project currentProject = this.projectManager.getCurrentProject();
    final UserSettings settings = currentProject.getSettings( MutableDiagramSettings.NAME );
    if ( settings instanceof DiagramSettings )
    {
      return ( DiagramSettings )settings;
    }

    // Overwrite the default created user settings object with our own. This
    // should be done implicitly, so make sure we keep the project's change flag
    // in the correct state...
    final MutableDiagramSettings diagramSettings = new MutableDiagramSettings( settings );

    final boolean oldChangedFlag = currentProject.isChanged();
    try
    {
      currentProject.setSettings( diagramSettings );
    }
    finally
    {
      currentProject.setChanged( oldChangedFlag );
    }

    return diagramSettings;
  }

  /**
   * Returns all available exporters.
   * 
   * @return an array of exporter names, never <code>null</code>, but an empty
   *         array is possible.
   */
  public String[] getExporterNames()
  {
    return getAllServiceNamesFor( Exporter.class );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String[] getExportExtensions( final String aExporterName )
  {
    final Exporter exporter = getExporter( aExporterName );
    if ( exporter == null )
    {
      return new String[0];
    }
    return exporter.getFilenameExtentions();
  }

  /**
   * {@inheritDoc}
   */
  public File getProjectFilename()
  {
    return this.projectManager.getCurrentProject().getFilename();
  }

  /**
   * Returns all available tools.
   * 
   * @return an array of tool names, never <code>null</code>, but an empty array
   *         is possible.
   */
  public String[] getToolNames()
  {
    return getAllServiceNamesFor( Tool.class );
  }

  /**
   * {@inheritDoc}
   */
  public void gotoCursorPosition( final int aCursorIdx )
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.isCursorsEnabled() )
    {
      final Long cursorPosition = this.dataContainer.getCursorPosition( aCursorIdx );
      if ( cursorPosition != null )
      {
        this.mainFrame.gotoPosition( cursorPosition.longValue() );
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void gotoFirstAvailableCursor()
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.isCursorsEnabled() )
    {
      for ( int c = 0; c < Ols.MAX_CURSORS; c++ )
      {
        if ( this.dataContainer.isCursorPositionSet( c ) )
        {
          final Long cursorPosition = this.dataContainer.getCursorPosition( c );
          if ( cursorPosition != null )
          {
            this.mainFrame.gotoPosition( cursorPosition.longValue() );
          }
          break;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void gotoLastAvailableCursor()
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.isCursorsEnabled() )
    {
      for ( int c = Ols.MAX_CURSORS - 1; c >= 0; c-- )
      {
        if ( this.dataContainer.isCursorPositionSet( c ) )
        {
          final Long cursorPosition = this.dataContainer.getCursorPosition( c );
          if ( cursorPosition != null )
          {
            this.mainFrame.gotoPosition( cursorPosition.longValue() );
          }
          break;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public void gotoTriggerPosition()
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.hasTriggerData() )
    {
      final long position = this.dataContainer.getTriggerPosition();
      this.mainFrame.gotoPosition( position );
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isDeviceSelected()
  {
    return this.selectedDevice != null;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isDeviceSetup()
  {
    return isDeviceSelected() && getDeviceController().isSetup();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isProjectChanged()
  {
    return this.projectManager.getCurrentProject().isChanged();
  }

  /**
   * {@inheritDoc}
   */
  public void openDataFile( final File aFile ) throws IOException
  {
    final FileReader reader = new FileReader( aFile );

    try
    {
      final Project tempProject = this.projectManager.createTemporaryProject();
      OlsDataHelper.read( tempProject, reader );

      setChannelLabels( tempProject.getChannelLabels() );
      setAcquisitionResult( tempProject.getCapturedData() );
      setCursorData( tempProject.getCursorPositions(), tempProject.isCursorsEnabled() );

      setStatus( "Capture data loaded from {0} ...", aFile.getName() );
    }
    finally
    {
      reader.close();

      zoomToFit();

      updateActions();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void openProjectFile( final File aFile ) throws IOException
  {
    FileInputStream fis = null;

    try
    {
      fis = new FileInputStream( aFile );

      this.projectManager.loadProject( fis );

      final Project project = this.projectManager.getCurrentProject();
      project.setFilename( aFile );

      zoomToFit();

      setStatus( "Project {0} loaded ...", project.getName() );
    }
    finally
    {
      HostUtils.closeResource( fis );
    }
  }

  /**
   * {@inheritDoc}
   */
  public void removeCursor( final int aCursorIdx )
  {
    if ( this.mainFrame != null )
    {
      this.dataContainer.setCursorPosition( aCursorIdx, null );
      fireCursorChangedEvent( aCursorIdx, -1 ); // removed...
    }

    updateActions();
  }

  /**
   * Removes a cursor change listener.
   * 
   * @param aListener
   *          the listener to remove, cannot be <code>null</code>.
   */
  public void removeCursorChangeListener( final DiagramCursorChangeListener aListener )
  {
    this.evenListeners.remove( DiagramCursorChangeListener.class, aListener );
  }

  /**
   * Removes the menu from the given provider from this controller, and does
   * this synchronously on the EDT.
   * 
   * @param aProvider
   *          the menu component provider, cannot be <code>null</code>.
   */
  public void removeMenu( final ComponentProvider aProvider )
  {
    SwingUtilities.invokeLater( new Runnable()
    {
      @Override
      public void run()
      {
        final JMenuBar menuBar = getMainMenuBar();
        if ( menuBar != null )
        {
          aProvider.removedFromContainer();

          menuBar.remove( aProvider.getComponent() );

          menuBar.revalidate();
          menuBar.repaint();
        }
      }
    } );
  }

  /**
   * {@inheritDoc}
   */
  public boolean repeatCaptureData( final Window aParent )
  {
    final DataAcquisitionService acquisitionService = getDataAcquisitionService();
    final DeviceController devCtrl = getDeviceController();
    if ( ( devCtrl == null ) || ( acquisitionService == null ) )
    {
      return false;
    }

    try
    {
      setStatus( "Capture from {0} started at {1,date,medium} {1,time,medium} ...", devCtrl.getName(), new Date() );

      acquisitionService.acquireData( devCtrl );

      return true;
    }
    catch ( IOException exception )
    {
      setStatus( "I/O problem: " + exception.getMessage() );

      exception.printStackTrace();

      // Make sure to handle IO-interrupted exceptions properly!
      HostUtils.handleInterruptedException( exception );

      return false;
    }
    finally
    {
      updateActions();
    }
  }

  /**
   * Runs the tool denoted by the given name.
   * 
   * @param aToolName
   *          the name of the tool to run, cannot be <code>null</code>;
   * @param aParent
   *          the parent window to use, can be <code>null</code>.
   */
  public void runTool( final String aToolName, final Window aParent )
  {
    if ( LOG.isLoggable( Level.INFO ) )
    {
      LOG.log( Level.INFO, "Running tool: \"{0}\" ...", aToolName );
    }

    final Tool tool = getTool( aToolName );
    if ( tool == null )
    {
      JOptionPane.showMessageDialog( aParent, "No such tool found: " + aToolName, "Error ...",
          JOptionPane.ERROR_MESSAGE );
    }
    else
    {
      final ToolContext context = createToolContext();
      tool.process( aParent, this.dataContainer, context, this );
    }

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public void saveDataFile( final File aFile ) throws IOException
  {
    final FileWriter writer = new FileWriter( aFile );
    try
    {
      final Project tempProject = this.projectManager.createTemporaryProject();
      tempProject.setCapturedData( this.dataContainer );

      OlsDataHelper.write( tempProject, writer );

      setStatus( "Capture data saved to {0} ...", aFile.getName() );
    }
    finally
    {
      writer.flush();
      writer.close();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void saveProjectFile( final String aName, final File aFile ) throws IOException
  {
    FileOutputStream out = null;
    try
    {
      final Project project = this.projectManager.getCurrentProject();
      project.setFilename( aFile );
      project.setName( aName );

      out = new FileOutputStream( aFile );
      this.projectManager.saveProject( out );

      setStatus( "Project {0} saved ...", aName );
    }
    finally
    {
      HostUtils.closeResource( out );
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void selectDevice( final String aDeviceName )
  {
    this.selectedDevice = aDeviceName;
    // Make sure the action reflect the current situation...
    updateActionsOnEDT();
  }

  /**
   * Sets whether or not cursors are enabled.
   * 
   * @param aState
   *          <code>true</code> if the cursors should be enabled,
   *          <code>false</code> otherwise.
   */
  public void setCursorMode( final boolean aState )
  {
    this.dataContainer.setCursorEnabled( aState );
    // Reflect the change directly on the diagram...
    repaintMainFrame();

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public void setCursorPosition( final int aCursorIdx, final Point aLocation )
  {
    // Implicitly enable cursor mode, the user already had made its
    // intensions clear that he want to have this by opening up the
    // context menu anyway...
    setCursorMode( true );

    if ( this.mainFrame != null )
    {
      // Convert the mouse-position to a sample index...
      final long sampleIdx = this.mainFrame.convertMousePositionToSampleIndex( aLocation );

      this.dataContainer.setCursorPosition( aCursorIdx, Long.valueOf( sampleIdx ) );

      fireCursorChangedEvent( aCursorIdx, aLocation.x );
    }

    updateActions();
  }

  /**
   * @param aMainFrame
   *          the mainFrame to set
   */
  public void setMainFrame( final MainFrame aMainFrame )
  {
    if ( this.mainFrame != null )
    {
      this.projectManager.removePropertyChangeListener( this.mainFrame );
    }
    if ( aMainFrame != null )
    {
      this.projectManager.addPropertyChangeListener( aMainFrame );
    }

    this.mainFrame = aMainFrame;
  }

  /**
   * {@inheritDoc}
   */
  public final void setStatus( final String aMessage, final Object... aMessageArgs )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.setStatus( aMessage, aMessageArgs );
    }
  }

  /**
   * Shows the "about OLS" dialog on screen. the parent window to use, can be
   * <code>null</code>.
   */
  public void showAboutBox()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.showAboutBox();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void showBundlesDialog( final Window aOwner )
  {
    BundlesDialog dialog = new BundlesDialog( aOwner, this.bundleContext );
    if ( dialog.showDialog() )
    {
      dialog.dispose();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void showChannelLabelsDialog( final Window aParent )
  {
    if ( this.mainFrame != null )
    {
      DiagramLabelsDialog dialog = new DiagramLabelsDialog( aParent, this.dataContainer.getChannelLabels() );
      if ( dialog.showDialog() )
      {
        final String[] channelLabels = dialog.getChannelLabels();
        setChannelLabels( channelLabels );
      }

      dialog.dispose();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void showDiagramModeSettingsDialog( final Window aParent )
  {
    if ( this.mainFrame != null )
    {
      ModeSettingsDialog dialog = new ModeSettingsDialog( aParent, getDiagramSettings() );
      if ( dialog.showDialog() )
      {
        final DiagramSettings settings = dialog.getDiagramSettings();
        this.projectManager.getCurrentProject().setSettings( settings );
        diagramSettingsUpdated();
      }

      dialog.dispose();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void showPreferencesDialog( final Window aParent )
  {
    GeneralSettingsDialog dialog = new GeneralSettingsDialog( aParent, getDiagramSettings() );
    if ( dialog.showDialog() )
    {
      final DiagramSettings settings = dialog.getDiagramSettings();
      this.projectManager.getCurrentProject().setSettings( settings );
      diagramSettingsUpdated();
    }

    dialog.dispose();
  }

  /**
   * @see nl.lxtreme.ols.api.ProgressCallback#updateProgress(int)
   */
  @Override
  public void updateProgress( final int aPercentage )
  {
    acquisitionInProgress( aPercentage );
  }

  /**
   * {@inheritDoc}
   */
  public void zoomDefault()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomDefault();
    }

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public void zoomIn()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomIn();
    }

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public void zoomOut()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomOut();
    }

    updateActions();
  }

  /**
   * {@inheritDoc}
   */
  public void zoomToFit()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomToFit();
    }

    updateActions();
  }

  /**
   * Returns the current main frame.
   * 
   * @return the main frame, can be <code>null</code>.
   */
  final MainFrame getMainFrame()
  {
    return this.mainFrame;
  }

  /**
   * Returns the main menu bar.
   * 
   * @return the main menu bar, can be <code>null</code>.
   */
  final JMenuBar getMainMenuBar()
  {
    JMenuBar result = null;
    if ( this.mainFrame != null )
    {
      result = this.mainFrame.getJMenuBar();
    }
    return result;
  }

  /**
   * Sets dataAcquisitionService to the given value.
   * 
   * @param aDataAcquisitionService
   *          the dataAcquisitionService to set.
   */
  final void setDataAcquisitionService( final DataAcquisitionService aDataAcquisitionService )
  {
    this.dataAcquisitionService = aDataAcquisitionService;
  }

  /**
   * Sets projectManager to the given value.
   * 
   * @param aProjectManager
   *          the projectManager to set.
   */
  final void setProjectManager( final ProjectManager aProjectManager )
  {
    this.projectManager = aProjectManager;
    this.dataContainer = new DataContainer( this.projectManager );
  }

  /**
   * Updates the actions on the EventDispatchThread (EDT).
   */
  final void updateActionsOnEDT()
  {
    final Runnable runner = new Runnable()
    {
      public void run()
      {
        updateActions();
      }
    };
    if ( SwingUtilities.isEventDispatchThread() )
    {
      runner.run();
    }
    else
    {
      SwingUtilities.invokeLater( runner );
    }
  }

  /**
   * Creates the tool context denoting the range of samples that should be
   * analysed by a tool.
   * 
   * @return a tool context, never <code>null</code>.
   */
  private ToolContext createToolContext()
  {
    int startOfDecode = -1;
    int endOfDecode = -1;

    final int dataLength = this.dataContainer.getValues().length;
    if ( this.dataContainer.isCursorsEnabled() )
    {
      if ( this.dataContainer.isCursorPositionSet( 0 ) )
      {
        final Long cursor1 = this.dataContainer.getCursorPosition( 0 );
        startOfDecode = this.dataContainer.getSampleIndex( cursor1.longValue() ) - 1;
      }
      if ( this.dataContainer.isCursorPositionSet( 1 ) )
      {
        final Long cursor2 = this.dataContainer.getCursorPosition( 1 );
        endOfDecode = this.dataContainer.getSampleIndex( cursor2.longValue() ) + 1;
      }
    }
    else
    {
      startOfDecode = 0;
      endOfDecode = dataLength;
    }

    startOfDecode = Math.max( 0, startOfDecode );
    if ( ( endOfDecode < 0 ) || ( endOfDecode >= dataLength ) )
    {
      endOfDecode = dataLength - 1;
    }

    int channels = this.dataContainer.getChannels();
    if ( channels == Ols.NOT_AVAILABLE )
    {
      channels = Ols.MAX_CHANNELS;
    }

    int enabledChannels = this.dataContainer.getEnabledChannels();
    if ( enabledChannels == Ols.NOT_AVAILABLE )
    {
      enabledChannels = NumberUtils.getBitMask( channels );
    }

    return new DefaultToolContext( startOfDecode, endOfDecode, channels, enabledChannels );
  }

  /**
   * Should be called after the diagram settings are changed. This method will
   * cause the main frame to be updated.
   */
  private void diagramSettingsUpdated()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.diagramSettingsUpdated();
      repaintMainFrame();
    }
  }

  /**
   * @param aCursorIdx
   * @param aMouseXpos
   */
  private void fireCursorChangedEvent( final int aCursorIdx, final int aMouseXpos )
  {
    final DiagramCursorChangeListener[] listeners = this.evenListeners.getListeners( DiagramCursorChangeListener.class );
    for ( final DiagramCursorChangeListener listener : listeners )
    {
      if ( aMouseXpos >= 0 )
      {
        listener.cursorChanged( aCursorIdx, aMouseXpos );
      }
      else
      {
        listener.cursorRemoved( aCursorIdx );
      }
    }
  }

  /**
   * @param aServiceClass
   * @return
   */
  private String[] getAllServiceNamesFor( final Class<?> aServiceClass )
  {
    String[] result = this.osgiHelper.getAllServicePropertiesFor( Action.NAME, aServiceClass );
    Arrays.sort( result );
    return result;
  }

  /**
   * Returns the data acquisition service.
   * 
   * @return a data acquisition service, never <code>null</code>.
   */
  private DataAcquisitionService getDataAcquisitionService()
  {
    return this.dataAcquisitionService;
  }

  /**
   * {@inheritDoc}
   */
  private DeviceController getDeviceController( final String aName ) throws IllegalArgumentException
  {
    return this.osgiHelper.getService( DeviceController.class, Action.NAME, aName );
  }

  /**
   * {@inheritDoc}
   */
  private Exporter getExporter( final String aName ) throws IllegalArgumentException
  {
    return this.osgiHelper.getService( Exporter.class, Action.NAME, aName );
  }

  /**
   * {@inheritDoc}
   */
  private Tool getTool( final String aName ) throws IllegalArgumentException
  {
    return this.osgiHelper.getService( Tool.class, Action.NAME, aName );
  }

  /**
   * Dispatches a request to repaint the entire main frame.
   */
  private void repaintMainFrame()
  {
    SwingUtilities.invokeLater( new Runnable()
    {
      @Override
      public void run()
      {
        ClientController.this.mainFrame.repaint();
      }
    } );
  }

  /**
   * Sets the captured data and zooms the view to show all the data.
   * 
   * @param aData
   *          the new captured data to set, cannot be <code>null</code>.
   */
  private void setAcquisitionResult( final AcquisitionResult aData )
  {
    this.dataContainer.setCapturedData( aData );

    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomDefault();
    }
  }

  /**
   * Set the channel labels.
   * 
   * @param aChannelLabels
   *          the channel labels to set, cannot be <code>null</code>.
   */
  private void setChannelLabels( final String[] aChannelLabels )
  {
    if ( aChannelLabels != null )
    {
      this.dataContainer.setChannelLabels( aChannelLabels );
      this.mainFrame.setChannelLabels( aChannelLabels );
    }
  }

  /**
   * @param aCursorData
   *          the cursor positions to set, cannot be <code>null</code>;
   * @param aCursorsEnabled
   *          <code>true</code> if cursors should be enabled, <code>false</code>
   *          if they should be disabled.
   */
  private void setCursorData( final Long[] aCursorData, final boolean aCursorsEnabled )
  {
    this.dataContainer.setCursorEnabled( aCursorsEnabled );
    for ( int i = 0; i < Ols.MAX_CURSORS; i++ )
    {
      this.dataContainer.setCursorPosition( i, aCursorData[i] );
    }
  }

  /**
   * Synchronizes the state of the actions to the current state of this host.
   */
  private void updateActions()
  {
    final DataAcquisitionService acquisitionService = getDataAcquisitionService();
    final DeviceController deviceCtrl = getDeviceController();

    final boolean deviceCapturing = ( acquisitionService != null ) && acquisitionService.isAcquiring();
    final boolean deviceControllerSet = deviceCtrl != null;
    final boolean deviceSetup = deviceControllerSet && !deviceCapturing && deviceCtrl.isSetup();

    getAction( CaptureAction.ID ).setEnabled( deviceControllerSet );
    getAction( CancelCaptureAction.ID ).setEnabled( deviceCapturing );
    getAction( RepeatCaptureAction.ID ).setEnabled( deviceSetup );

    final boolean projectChanged = this.projectManager.getCurrentProject().isChanged();
    final boolean projectSavedBefore = this.projectManager.getCurrentProject().getFilename() != null;
    final boolean dataAvailable = this.dataContainer.hasCapturedData();

    getAction( SaveProjectAction.ID ).setEnabled( projectChanged );
    getAction( SaveProjectAsAction.ID ).setEnabled( projectSavedBefore && projectChanged );
    getAction( SaveDataFileAction.ID ).setEnabled( dataAvailable );

    getAction( ZoomInAction.ID ).setEnabled( dataAvailable );
    getAction( ZoomOutAction.ID ).setEnabled( dataAvailable );
    getAction( ZoomDefaultAction.ID ).setEnabled( dataAvailable );
    getAction( ZoomFitAction.ID ).setEnabled( dataAvailable );

    final boolean triggerEnable = dataAvailable && this.dataContainer.hasTriggerData();
    getAction( GotoTriggerAction.ID ).setEnabled( triggerEnable );

    // Update the cursor actions accordingly...
    getAction( SetCursorModeAction.ID ).setEnabled( dataAvailable );
    getAction( SetCursorModeAction.ID ).putValue( Action.SELECTED_KEY,
        Boolean.valueOf( this.dataContainer.isCursorsEnabled() ) );

    final boolean enableCursors = dataAvailable && this.dataContainer.isCursorsEnabled();

    boolean anyCursorSet = false;
    for ( int c = 0; c < Ols.MAX_CURSORS; c++ )
    {
      final boolean cursorPositionSet = this.dataContainer.isCursorPositionSet( c );
      anyCursorSet |= cursorPositionSet;

      final boolean gotoCursorNEnabled = enableCursors && cursorPositionSet;
      getAction( GotoNthCursorAction.getID( c ) ).setEnabled( gotoCursorNEnabled );

      final Action action = getAction( SetCursorAction.getCursorId( c ) );
      action.setEnabled( dataAvailable );
      action.putValue( Action.SELECTED_KEY, Boolean.valueOf( cursorPositionSet ) );
    }

    getAction( GotoFirstCursorAction.ID ).setEnabled( enableCursors && anyCursorSet );
    getAction( GotoLastCursorAction.ID ).setEnabled( enableCursors && anyCursorSet );

    getAction( ClearCursors.ID ).setEnabled( enableCursors && anyCursorSet );

    // Update the tools...
    final IManagedAction[] toolActions = this.actionManager.getActionByType( RunToolAction.class );
    for ( IManagedAction toolAction : toolActions )
    {
      toolAction.setEnabled( dataAvailable );
    }

    // Update the exporters...
    final IManagedAction[] exportActions = this.actionManager.getActionByType( ExportAction.class );
    for ( IManagedAction exportAction : exportActions )
    {
      exportAction.setEnabled( dataAvailable );
    }
  }
}
