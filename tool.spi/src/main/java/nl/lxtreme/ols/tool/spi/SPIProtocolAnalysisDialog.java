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
 * Copyright (C) 2006-2010 Michael Poppitz, www.sump.org
 * Copyright (C) 2010 J.W. Janssen, www.lxtreme.nl
 */
package nl.lxtreme.ols.tool.spi;


import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;

import nl.lxtreme.ols.tool.base.*;
import nl.lxtreme.ols.util.*;
import nl.lxtreme.ols.util.ExportUtils.*;
import nl.lxtreme.ols.util.swing.*;


/**
 * The Dialog Class
 * 
 * @author Frank Kunz The dialog class draws the basic dialog with a grid
 *         layout. The dialog consists of three main parts. A settings panel, a
 *         table panel and three buttons.
 */
public final class SPIProtocolAnalysisDialog extends BaseAsyncToolDialog<SPIDataSet, SPIAnalyserWorker>
{
  // CONSTANTS

  private static final long serialVersionUID = 1L;

  private static final Logger LOG = Logger.getLogger( SPIProtocolAnalysisDialog.class.getName() );

  // VARIABLES

  private final String[] modearray;
  private final String[] bitarray;
  private final String[] orderarray;
  private final JComboBox sck;
  private final JComboBox miso;
  private final JComboBox mosi;
  private final JComboBox cs;
  private final JComboBox mode;
  private final JComboBox bits;
  private final JEditorPane outText;
  private final JComboBox order;

  private final RunAnalysisAction runAnalysisAction;
  private final ExportAction exportAction;
  private final CloseAction closeAction;

  // CONSTRUCTORS

  /**
   * @param aOwner
   * @param aName
   */
  public SPIProtocolAnalysisDialog( final Window aOwner, final String aName )
  {
    super( aOwner, aName );

    Container pane = getContentPane();
    pane.setLayout( new GridBagLayout() );
    getRootPane().setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );

    /*
     * add protocol settings elements
     */
    JPanel panSettings = new JPanel();
    panSettings.setLayout( new GridLayout( 7, 2, 5, 5 ) );
    panSettings.setBorder( BorderFactory.createCompoundBorder( BorderFactory.createTitledBorder( "Settings" ),
        BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) ) );

    String channels[] = new String[32];
    for ( int i = 0; i < 32; i++ )
    {
      channels[i] = new String( "Channel " + i );
    }

    panSettings.add( new JLabel( "SCK" ) );
    this.sck = new JComboBox( channels );
    panSettings.add( this.sck );

    panSettings.add( new JLabel( "MISO" ) );
    this.miso = new JComboBox( channels );
    panSettings.add( this.miso );

    panSettings.add( new JLabel( "MOSI" ) );
    this.mosi = new JComboBox( channels );
    panSettings.add( this.mosi );

    panSettings.add( new JLabel( "/CS" ) );
    this.cs = new JComboBox( channels );
    panSettings.add( this.cs );

    panSettings.add( new JLabel( "Mode" ) );
    this.modearray = new String[4];
    for ( int i = 0; i < this.modearray.length; i++ )
    {
      this.modearray[i] = new String( "" + i );
    }
    this.mode = new JComboBox( this.modearray );
    panSettings.add( this.mode );

    panSettings.add( new JLabel( "Bits" ) );
    this.bitarray = new String[13];
    for ( int i = 0; i < this.bitarray.length; i++ )
    {
      this.bitarray[i] = new String( "" + ( i + 4 ) );
    }
    this.bits = new JComboBox( this.bitarray );
    this.bits.setSelectedItem( "8" );
    panSettings.add( this.bits );

    panSettings.add( new JLabel( "Order" ) );
    this.orderarray = new String[2];
    this.orderarray[0] = new String( "MSB first" );
    this.orderarray[1] = new String( "LSB first" );
    this.order = new JComboBox( this.orderarray );
    panSettings.add( this.order );
    pane.add( panSettings, createConstraints( 0, 0, 1, 1, 0, 0 ) );

    /*
     * add an empty output view
     */
    JPanel panTable = new JPanel();
    panTable.setLayout( new GridLayout( 1, 1, 5, 5 ) );
    panTable.setBorder( BorderFactory.createCompoundBorder( BorderFactory.createTitledBorder( "Results" ),
        BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) ) );
    this.outText = new JEditorPane( "text/html", getEmptyHtmlPage() );
    this.outText.setMargin( new Insets( 5, 5, 5, 5 ) );
    panTable.add( new JScrollPane( this.outText ) );
    add( panTable, createConstraints( 1, 0, 3, 3, 1.0, 1.0 ) );

    /*
     * add buttons
     */
    this.runAnalysisAction = new RunAnalysisAction();
    JButton btnConvert = new JButton( this.runAnalysisAction );
    add( btnConvert, createConstraints( 0, 3, 1, 1, 0.5, 0 ) );

    this.exportAction = new ExportAction();
    JButton btnExport = new JButton( this.exportAction );
    add( btnExport, createConstraints( 1, 3, 1, 1, 0.5, 0 ) );

    this.closeAction = new CloseAction();
    JButton btnCancel = new JButton( this.closeAction );
    add( btnCancel, createConstraints( 2, 3, 1, 1, 0.5, 0 ) );

    setSize( 1000, 500 );
    setResizable( false );
  }

  // METHODS

  /**
   * @param x
   * @param y
   * @param w
   * @param h
   * @param wx
   * @param wy
   * @return
   */
  private static GridBagConstraints createConstraints( final int x, final int y, final int w, final int h,
      final double wx, final double wy )
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets( 4, 4, 4, 4 );
    gbc.gridx = x;
    gbc.gridy = y;
    gbc.gridwidth = w;
    gbc.gridheight = h;
    gbc.weightx = wx;
    gbc.weighty = wy;
    return ( gbc );
  }

  public void actionPerformed( final ActionEvent e )
  {
  }

  /**
   * This is the SPI protocol decoder core The decoder scans for a decode start
   * event like CS high to low edge or the trigger of the captured data. After
   * this the decoder starts to decode the data by the selected mode, number of
   * bits and bit order. The decoded data are put to a JTable object directly.
   */
  @Override
  public void onToolWorkerReady( final SPIDataSet aAnalysisResult )
  {
    super.onToolWorkerReady( aAnalysisResult );

    this.outText.setText( toHtmlPage( aAnalysisResult ) );
    this.outText.setEditable( false );

    this.exportAction.setEnabled( !aAnalysisResult.isEmpty() );
    this.runAnalysisAction.restore();
    this.runAnalysisAction.setEnabled( false );
  }

  /**
   * @see nl.lxtreme.ols.api.Configurable#readProperties(java.lang.String,
   *      java.util.Properties)
   */
  public void readProperties( final String aNamespace, final Properties aProperties )
  {
    SwingComponentUtils.setSelectedItem( this.sck, aProperties.getProperty( aNamespace + ".sck" ) );
    SwingComponentUtils.setSelectedItem( this.miso, aProperties.getProperty( aNamespace + ".miso" ) );
    SwingComponentUtils.setSelectedItem( this.mosi, aProperties.getProperty( aNamespace + ".mosi" ) );
    SwingComponentUtils.setSelectedItem( this.cs, aProperties.getProperty( aNamespace + ".cs" ) );
    SwingComponentUtils.setSelectedItem( this.mode, this.modearray, aProperties.getProperty( aNamespace + ".mode" ) );
    SwingComponentUtils.setSelectedItem( this.bits, this.bitarray, aProperties.getProperty( aNamespace + ".bits" ) );
    SwingComponentUtils.setSelectedItem( this.order, this.orderarray, aProperties.getProperty( aNamespace + ".order" ) );
  }

  /**
   * @see nl.lxtreme.ols.tool.base.ToolDialog#reset()
   */
  @Override
  public void reset()
  {
    this.outText.setText( getEmptyHtmlPage() );
    this.outText.setEditable( false );

    this.exportAction.setEnabled( false );

    this.runAnalysisAction.restore();
    this.runAnalysisAction.setEnabled( true );

    setControlsEnabled( true );
  }

  /**
   * @see nl.lxtreme.ols.api.Configurable#writeProperties(java.lang.String,
   *      java.util.Properties)
   */
  public void writeProperties( final String aNamespace, final Properties aProperties )
  {
    aProperties.setProperty( aNamespace + ".sck", Integer.toString( this.sck.getSelectedIndex() ) );
    aProperties.setProperty( aNamespace + ".miso", Integer.toString( this.miso.getSelectedIndex() ) );
    aProperties.setProperty( aNamespace + ".mosi", Integer.toString( this.mosi.getSelectedIndex() ) );
    aProperties.setProperty( aNamespace + ".cs", Integer.toString( this.cs.getSelectedIndex() ) );
    aProperties.setProperty( aNamespace + ".mode", ( String )this.mode.getSelectedItem() );
    aProperties.setProperty( aNamespace + ".bits", ( String )this.bits.getSelectedItem() );
    aProperties.setProperty( aNamespace + ".order", ( String )this.order.getSelectedItem() );
  }

  /**
   * set the controls of the dialog enabled/disabled
   * 
   * @param aEnable
   *          status of the controls
   */
  @Override
  protected void setControlsEnabled( final boolean aEnable )
  {
    this.sck.setEnabled( aEnable );
    this.miso.setEnabled( aEnable );
    this.mosi.setEnabled( aEnable );
    this.cs.setEnabled( aEnable );
    this.mode.setEnabled( aEnable );
    this.bits.setEnabled( aEnable );
    this.order.setEnabled( aEnable );

    this.exportAction.setEnabled( aEnable );
    this.closeAction.setEnabled( aEnable );
  }

  /**
   * @see nl.lxtreme.ols.tool.base.BaseAsyncToolDialog#setupToolWorker(nl.lxtreme.ols.tool.base.BaseAsyncToolWorker)
   */
  @Override
  protected void setupToolWorker( final SPIAnalyserWorker aToolWorker )
  {
    aToolWorker.setBitCount( Integer.parseInt( ( String )this.bits.getSelectedItem() ) - 1 );
    aToolWorker.setCSMask( 1 << this.cs.getSelectedIndex() );
    aToolWorker.setSCKMask( 1 << this.sck.getSelectedIndex() );
    aToolWorker.setMisoMask( 1 << this.miso.getSelectedIndex() );
    aToolWorker.setMosiMask( 1 << this.mosi.getSelectedIndex() );
    aToolWorker.setOrder( "MSB first".equals( this.order.getSelectedItem() ) ? Endianness.MSB_FIRST
        : Endianness.LSB_FIRST );
    aToolWorker.setMode( SPIMode.parse( ( String )this.mode.getSelectedItem() ) );
  }

  /**
   * exports the table data to a CSV file
   * 
   * @param aFile
   *          File object
   */
  @Override
  protected void storeToCsvFile( final File aFile, final SPIDataSet aDataSet )
  {
    try
    {
      final CsvExporter exporter = ExportUtils.createCsvExporter( aFile );

      exporter.setHeaders( "index", "time", "event?", "event-type", "MOSI data", "MISO data" );

      final List<SPIData> decodedData = aDataSet.getData();
      for ( int i = 0; i < decodedData.size(); i++ )
      {
        final SPIData ds = decodedData.get( i );

        exporter.addRow( i, ds.getTimeDisplayValue(), ds.isEvent(), ds.getEvent(), ds.getMoSiValue(), ds
            .getMiSoValue() );
      }

      exporter.close();
    }
    catch ( IOException exception )
    {
      if ( LOG.isLoggable( Level.WARNING ) )
      {
        LOG.log( Level.WARNING, "CSV export failed!", exception );
      }
    }
  }

  /**
   * stores the data to a HTML file
   * 
   * @param aFile
   *          file object
   */
  @Override
  protected void storeToHtmlFile( final File aFile, final SPIDataSet aDataSet )
  {
    if ( !aDataSet.isEmpty() )
    {
      System.out.println( "writing decoded data to " + aFile.getPath() );

      try
      {
        BufferedWriter bw = new BufferedWriter( new FileWriter( aFile ) );

        // write the complete displayed html page to file
        bw.write( this.outText.getText() );

        bw.close();
      }
      catch ( Exception E )
      {
        E.printStackTrace( System.out );
      }
    }
  }

  /**
   * Generates an empty HTML page.
   * 
   * @return String with HTML data.
   */
  private String getEmptyHtmlPage()
  {
    Date now = new Date();
    DateFormat df = DateFormat.getDateInstance( DateFormat.LONG );

    // generate html page header
    String header = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">"
        + "<html>"
        + "  <head>"
        + "    <title></title>"
        + "    <meta content=\"\">"
        + "    <style>"
        + "           th { text-align:left;font-style:italic;font-weight:bold;font-size:medium;font-family:sans-serif;background-color:#C0C0FF; }"
        + "       </style>" + "  </head>" + "   <body>" + "       <H2>SPI Analysis Results</H2>" + "       <hr>"
        + "           <div style=\"text-align:right;font-size:x-small;\">" + df.format( now ) + "           </div>"
        + "       <br>";

    // generate the data table
    String data = "<table style=\"font-family:monospace;width:100%;\">"
        + "<tr><th style=\"width:15%;\">Index</th><th style=\"width:15%;\">Time</th><th style=\"width:10%;\">MOSI Hex</th><th style=\"width:10%;\">MOSI Bin</th><th style=\"width:8%;\">MOSI Dec</th><th style=\"width:7%;\">MOSI ASCII</th><th style=\"width:10%;\">MISO Hex</th><th style=\"width:10%;\">MISO Bin</th><th style=\"width:8%;\">MISO Dec</th><th style=\"width:7%;\">MISO ASCII</th></tr>";
    data = data.concat( "</table" );

    // generate the footer table
    String footer = "   </body>" + "</html>";

    return ( header + data + footer );
  }

  /**
   * generate a HTML page
   * 
   * @param aDataSet
   *          the data set to create the HTML page for, cannot be
   *          <code>null</code>.
   * @return String with HTML data
   */
  private String toHtmlPage( final SPIDataSet aDataSet )
  {
    Date now = new Date();
    DateFormat df = DateFormat.getDateInstance( DateFormat.LONG, Locale.US );

    int bitCount = Integer.parseInt( ( String )this.bits.getSelectedItem() );
    int bitAdder = 0;

    if ( bitCount % 4 != 0 )
    {
      bitAdder = 1;
    }

    // generate html page header
    String header = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">"
        + "<html>"
        + "  <head>"
        + "    <title></title>"
        + "    <meta content=\"\">"
        + "    <style>"
        + "           th { text-align:left;font-style:italic;font-weight:bold;font-size:medium;font-family:sans-serif;background-color:#C0C0FF; }"
        + "       </style>" + "  </head>" + "   <body>" + "       <H2>SPI Analysis Results</H2>" + "       <hr>"
        + "           <div style=\"text-align:right;font-size:x-small;\">" + df.format( now ) + "           </div>"
        + "       <br>";

    // generate the data table
    String data = "<table style=\"font-family:monospace;width:100%;\">"
        + "<tr><th style=\"width:15%;\">Index</th><th style=\"width:15%;\">Time</th><th style=\"width:10%;\">MOSI Hex</th><th style=\"width:10%;\">MOSI Bin</th><th style=\"width:8%;\">MOSI Dec</th><th style=\"width:7%;\">MOSI ASCII</th><th style=\"width:10%;\">MISO Hex</th><th style=\"width:10%;\">MISO Bin</th><th style=\"width:8%;\">MISO Dec</th><th style=\"width:7%;\">MISO ASCII</th></tr>";
    final List<SPIData> decodedData = aDataSet.getData();
    for ( int i = 0; i < decodedData.size(); i++ )
    {
      SPIData ds = decodedData.get( i );
      if ( ds.isEvent() )
      {
        // this is an event
        if ( SPIDataSet.SPI_CS_LOW.equals( ds.getEvent() ) )
        {
          // start condition
          data = data.concat( "<tr style=\"background-color:#E0E0E0;\"><td>" + i + "</td><td>"
              + ds.getTimeDisplayValue()
              + "</td><td>CSLOW</td><td></td><td></td><td></td><td>CSLOW</td><td></td><td></td><td></td></tr>" );
        }
        else if ( SPIDataSet.SPI_CS_HIGH.equals( ds.getEvent() ) )
        {
          // stop condition
          data = data.concat( "<tr style=\"background-color:#E0E0E0;\"><td>" + i + "</td><td>"
              + ds.getTimeDisplayValue()
              + "</td><td>CSHIGH</td><td></td><td></td><td></td><td>CSHIGH</td><td></td><td></td><td></td></tr>" );
        }
        else
        {
          // unknown event
          data = data.concat( "<tr style=\"background-color:#FF8000;\"><td>" + i + "</td><td>"
              + ds.getTimeDisplayValue()
              + "</td><td>UNKNOWN</td><td></td><td></td><td></td><td>UNKNOWN</td><td></td><td></td><td></td></tr>" );
        }
      }
      else
      {
        final int mosiValue = ds.getMoSiValue();
        final int misoValue = ds.getMiSoValue();

        data = data.concat( "<tr style=\"background-color:#FFFFFF;\"><td>" + i + "</td><td>" + ds.getTimeDisplayValue()
            + "</td><td>" + "0x" + DisplayUtils.integerToHexString( mosiValue, bitCount / 4 + bitAdder ) + "</td><td>"
            + "0b" + DisplayUtils.integerToBinString( mosiValue, bitCount ) + "</td><td>" + mosiValue + "</td><td>" );

        if ( ( bitCount == 8 ) && Character.isLetterOrDigit( ( char )mosiValue ) )
        {
          data += ( char )mosiValue;
        }

        data = data.concat( "</td><td>" + "0x" + DisplayUtils.integerToHexString( misoValue, bitCount / 4 + bitAdder )
            + "</td><td>" + "0b" + DisplayUtils.integerToBinString( misoValue, bitCount ) + "</td><td>" + misoValue
            + "</td><td>" );
        if ( ( bitCount == 8 ) && Character.isLetterOrDigit( ( char )misoValue ) )
        {
          data += ( char )misoValue;
        }

        data = data.concat( "</td></tr>" );
      }
    }
    data = data.concat( "</table" );

    // generate the footer table
    String footer = "   </body>" + "</html>";

    return ( header + data + footer );
  }
}