/*
 *  jETeL/Clover - Java based ETL application framework.
 *  Copyright (C) 2003,2002  David Pavlis, Wes Maciorowski
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.jetel.gui.fileformatwizard;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import java.awt.GridBagLayout;
import javax.swing.JLabel;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Font;
import javax.swing.JTextPane;

import org.jetel.gui.component.FormInterface;
import org.jetel.gui.component.RulerPanel;
import org.jetel.util.QSortAlgorithm;

import java.awt.SystemColor;

public class Screen2f extends JPanel implements  FormInterface
{
  private GridBagLayout gridBagLayout1 = new GridBagLayout();
  private JLabel jLabel1 = new JLabel();
  private JLabel jLabel2 = new JLabel();
  private RulerPanel aRulerPanel = null;
  private JPanel jPanel2 = new JPanel();
  private JLabel jLabel3 = new JLabel();
  private JTextPane jTextPane1 = new JTextPane();
	 
  private FileFormatDispatcher aDispatcher;
  private FileFormatDataModel aFileFormatParameters;
  private short[] fieldWidths;	
  
  public Screen2f(FileFormatDispatcher aDispatcher, FileFormatDataModel aFileFormatParameters)
  {
	this.aDispatcher = aDispatcher;
	this.aFileFormatParameters = aFileFormatParameters;
  	
	try
	{
		aRulerPanel = new RulerPanel(new Font("Courier New", 0, 11));
	  jbInit();
	}
	catch(Exception e)
	{
	  e.printStackTrace();
	}

  }
  public Screen2f()
  {
	try
	{
		aRulerPanel = new RulerPanel(new Font("Courier New", 0, 11));
	  jbInit();
	}
	catch(Exception e)
	{
	  e.printStackTrace();
	}

  }

  private void jbInit() throws Exception
  {
    this.setLayout(gridBagLayout1);
    jLabel1.setText("Screen 2 of 4");
    jLabel1.setFont(new Font("Dialog", 1, 11));
    jLabel2.setText("Specify Fields");
    jTextPane1.setText("Here goes screen help text.");
    jTextPane1.setBackground(SystemColor.control);
    
	loadData();
	
    this.add(jLabel1, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0));
    this.add(jLabel2, new GridBagConstraints(1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
	JScrollPane scrollPane = new JScrollPane(aRulerPanel,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
	this.add(scrollPane, new GridBagConstraints(0, 3, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 0, 0));
	this.setPreferredSize(new Dimension(300, 200));
    this.add(jPanel2, new GridBagConstraints(0, 4, 2, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    this.add(jLabel3, new GridBagConstraints(0, 2, 2, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
    this.add(jTextPane1, new GridBagConstraints(0, 1, 2, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
  }


  /**
   *  Setter for linesFromFile
   *
   *@param  linesFromFile           A String arrary containing first few lines in the file.
   *@since                   		April 2, 2003
   */
	public void setLinesFromFile(String[] linesFromFile) {
		aRulerPanel.setLinesFromFile( linesFromFile);
		repaint();
	}
	
	/**
	 *  Getter for linesFromFile
	 *
	 *@return                  linesFromFile	A String arrary.
	 *@since                   April 2, 2003
	 */
	public String[] getLinesFromFile() {
		return aRulerPanel.getLinesFromFile();
	}
	
	public void loadData() {
		   if(aFileFormatParameters != null) {
				if(aFileFormatParameters.fileName != null) {   	
				  jLabel3.setText(aFileFormatParameters.fileName);
				}
				if(aFileFormatParameters.linesFromFile != null) {
				  setLinesFromFile(aFileFormatParameters.linesFromFile);
					
				}
		   } else {
			  jLabel3.setText("filename goes here...");
		   }
	}

	/* (non-Javadoc)
	 * @see org.jetel.gui.component.PhasedPanelInterface#validateData()
	 */
	public boolean validateData() {
		int oldPos = 0;
		int newPos = 0;
		Object[] objects = aRulerPanel.getPointList();
		int[] tmpFieldPos = new int[objects.length];
		for(int i=0; i < objects.length ; i++ ) {
			tmpFieldPos[i] = ((Integer)objects[i]).intValue();
		}
		try {
			QSortAlgorithm.sort(tmpFieldPos);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		short[] fieldWidths = new short[objects.length+1];
		
		for(int i=0; i < tmpFieldPos.length ; i++ ) {
			newPos = tmpFieldPos[i] -1;
			fieldWidths[i]=(short)(newPos - oldPos );
			oldPos = newPos;
		}
		String[] tmp = getLinesFromFile();				
		fieldWidths[fieldWidths.length-1]=(short)(tmp[0].length()-oldPos);
		if(fieldWidths[fieldWidths.length-1]>0) { 
			this.fieldWidths = fieldWidths;
			return true;
		}
			
		return false;
	}
	/* (non-Javadoc)
	 * @see org.jetel.gui.component.PhasedPanelInterface#saveData()
	 */
	public void saveData() {
		aFileFormatParameters.recordSizes = fieldWidths;
		aFileFormatParameters.recordMeta.bulkLoadFieldSizes(fieldWidths);
	}

}