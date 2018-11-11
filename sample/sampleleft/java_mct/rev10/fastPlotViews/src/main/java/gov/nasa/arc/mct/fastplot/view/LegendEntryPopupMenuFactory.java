/*******************************************************************************
 * Mission Control Technologies, Copyright (c) 2009-2012, United States Government
 * as represented by the Administrator of the National Aeronautics and Space 
 * Administration. All rights reserved.
 *
 * The MCT platform is licensed under the Apache License, Version 2.0 (the 
 * "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 *
 * MCT includes source code licensed under additional open source licenses. See 
 * the MCT Open Source Licenses file included with this distribution or the About 
 * MCT Licenses dialog available at runtime from the MCT Help menu for additional 
 * information. 
 *******************************************************************************/
package gov.nasa.arc.mct.fastplot.view;

import gov.nasa.arc.mct.fastplot.bridge.LegendEntry;
import gov.nasa.arc.mct.fastplot.bridge.PlotConstants;
import gov.nasa.arc.mct.fastplot.bridge.PlotLineColorPalette;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ResourceBundle;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFormattedTextField;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSpinner;
import javax.swing.JSpinner.NumberEditor;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;

/**
 * Provides popup menus to legend entries upon request. 
 * @author vwoeltje
 */
public class LegendEntryPopupMenuFactory {
	private static final ResourceBundle BUNDLE = 
        ResourceBundle.getBundle(LegendEntryPopupMenuFactory.class.getName().substring(0, 
        		LegendEntryPopupMenuFactory.class.getName().lastIndexOf("."))+".Bundle");
	
	private PlotViewManifestation manifestation;

	public LegendEntryPopupMenuFactory(PlotViewManifestation targetPlotManifestation) {
		manifestation = targetPlotManifestation;
	}
	
	/**
	 * Get a popup menu for a specified legend entry
	 * @param entry the legend entry to produce a popup menu
	 * @return a popup menu with options appropriate to the specified legend entry
	 */
	public JPopupMenu getPopup(LegendEntry entry) {
		LegendEntryPopup popup = new LegendEntryPopup(manifestation, entry);
		return popup;
	}
	
	private class LegendEntryPopup extends JPopupMenu {
		private static final long serialVersionUID = -4846098785335776279L;
		
		public LegendEntryPopup(final PlotViewManifestation manifestation, final LegendEntry legendEntry) {
			super();
			
			Color assigned = legendEntry.getForeground();
			
			String name = legendEntry.getComputedBaseDisplayName();
			if (name.isEmpty()) name = legendEntry.getFullBaseDisplayName();
			
			String subMenuText1 = String.format(BUNDLE.getString("SelectColor.label"), 
			                     name);
			String subMenuText2 = String.format(BUNDLE.getString("RegressionPointsLabel"), 
                    name);
			final JMenu subMenu1 = new JMenu(subMenuText1);
			final JMenuItem regressionLineCheckBox = new JCheckBoxMenuItem(BUNDLE.getString("RegressionLineLabel"),false);
			final JMenu regressionMenu = new JMenu(subMenuText2);
			
			
			SpinnerModel pointsModel = new SpinnerNumberModel(legendEntry.getNumberRegressionPoints(), 2, 100, 1);
			final JSpinner spinner = new JSpinner(pointsModel);
			spinner.setPreferredSize(new Dimension(50, 20));
			spinner.setBorder(new EmptyBorder(2,2,2,2));
			spinner.addChangeListener(new ChangeListener() {

				@Override
				public void stateChanged(ChangeEvent e) {
					legendEntry.setNumberRegressionPoints(Integer.parseInt(((JSpinner)e.getSource()).getValue().toString()));
					manifestation.setupRegressionLines();
				}
				
			});
			
			 final JFormattedTextField myTextField = ((NumberEditor) spinner
				        .getEditor()).getTextField();
			
			spinner.addKeyListener(new KeyListener() {

				@Override
				public void keyTyped(KeyEvent e) {
					if ( ! (e.getKeyChar() == KeyEvent.CHAR_UNDEFINED) && 
							(e.getKeyCode() == KeyEvent.VK_UNDEFINED) &&
							// Apparently, backspace has a key char (although it should not)
							(e.getKeyChar() == '0' ||
							 e.getKeyChar() == '1' ||
							 e.getKeyChar() == '2' ||
							 e.getKeyChar() == '3' ||
							 e.getKeyChar() == '4' ||
							 e.getKeyChar() == '5' ||
							 e.getKeyChar() == '6' ||
							 e.getKeyChar() == '7' ||
							 e.getKeyChar() == '8' ||
							 e.getKeyChar() == '9'
									) &&
							Integer.valueOf(myTextField.getValue() + String.valueOf(e.getKeyChar())).compareTo((Integer) 
									((SpinnerNumberModel) spinner.getModel()).getMinimum()) > 0 && 
							Integer.valueOf(myTextField.getValue() + String.valueOf(e.getKeyChar())).compareTo((Integer) 
									((SpinnerNumberModel) spinner.getModel()).getMaximum()) < 0 ) {
						myTextField.setText(myTextField.getValue() + String.valueOf(e.getKeyChar()));
						
					}
				}

				@Override
				public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_DELETE ) {
						((NumberEditor) spinner.getEditor()).getTextField().setText("");
					} 
					myTextField.grabFocus();
				}

				@Override
				public void keyReleased(KeyEvent e) {
				}
				
			});  
				 
			 myTextField.addFocusListener(new FocusListener()
				    {
				 @Override
				 public void focusGained(FocusEvent e) {
					 SwingUtilities.invokeLater(new Runnable() {
				            public void run() {
				            	myTextField.selectAll();
				            }
				     });
				 }

				@Override
				public void focusLost(java.awt.event.FocusEvent e) {
				}
			});
			 
			 final NumberEditor numberEditor = (NumberEditor) spinner.getEditor();
			 
			 numberEditor.addKeyListener(new KeyListener() {

				@Override
				public void keyTyped(KeyEvent e) {
				}

				@Override
				public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_LEFT && 
							numberEditor.getTextField().getCaretPosition() == 0) {
						regressionMenu.setSelected(true);
					} 
				}

				@Override
				public void keyReleased(KeyEvent e) {
				}
			});
			 
			 myTextField.addKeyListener(new KeyListener() {

				@Override
				public void keyTyped(KeyEvent e) {
				}

				@Override
				public void keyPressed(KeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_LEFT && 
							numberEditor.getTextField().getCaretPosition() == 0) {
						regressionMenu.setSelected(true);
						regressionMenu.grabFocus();
						((JPopupMenu) spinner.getParent()).setSelected(regressionMenu);
					} 
				}

				@Override
				public void keyReleased(KeyEvent e) {
				}
				
			});
			 
			regressionMenu.addMenuKeyListener(new MenuKeyListener() {

				@Override
				public void menuKeyTyped(MenuKeyEvent e) {
				}

				@Override
				public void menuKeyPressed(MenuKeyEvent e) {
					if (e.getKeyCode() == KeyEvent.VK_RIGHT ) {
						spinner.setVisible(true);
						spinner.requestFocus();
						((NumberEditor) spinner.getEditor()).grabFocus();
					} 
				}

				@Override
				public void menuKeyReleased(MenuKeyEvent e) {
				}
				
			});
			
			
			if (!manifestation.isLocked()) {
				for (int i = 0; i < PlotConstants.MAX_NUMBER_OF_DATA_ITEMS_ON_A_PLOT; i++) {
					JMenuItem item = new JRadioButtonMenuItem("", 
							new SolidColorIcon(PlotLineColorPalette.getColor(i)),
							(assigned == PlotLineColorPalette.getColor(i))
							);
					final int colorIndex = i;
					item.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {				
							legendEntry.setForeground(PlotLineColorPalette.getColor(colorIndex));
							manifestation.setupPlotLineColors();
						}					
					});
					subMenu1.add(item);
				}
				
				add(subMenu1);
				addSeparator();
				
				regressionLineCheckBox.addActionListener(new ActionListener() {

					@Override
					public void actionPerformed(ActionEvent e) {
						AbstractButton abstractButton = (AbstractButton) e.getSource();
						if (abstractButton.getModel().isSelected()) {
							legendEntry.setHasRegressionLine(true);
						} else {
							legendEntry.setHasRegressionLine(false);
						}
						manifestation.setupRegressionLines();
						
					}
					
				});
				if (legendEntry.hasRegressionLine()) {
					regressionLineCheckBox.setSelected(true);
				} else {
					regressionLineCheckBox.setSelected(false);
				}
				add(regressionLineCheckBox);
				regressionMenu.add(spinner);
				add(regressionMenu);
			}
			
		}	
		
		private class SolidColorIcon implements Icon {
			private Color iconColor;
						
			public SolidColorIcon (Color c) {
				iconColor = c;
			}

			@Override
			public int getIconHeight() {
				return 12;
			}

			@Override
			public int getIconWidth() {
				return 48;
			}

			@Override
			public void paintIcon(Component arg0, Graphics g, int x,
					int y) {
				g.setColor(iconColor);
				g.fillRect(x, y, getIconWidth(), getIconHeight() - 1);
				g.setColor(iconColor.darker());
				g.drawRect(x, y, getIconWidth(), getIconHeight() - 1);				
			}
			
		}
	}

}
