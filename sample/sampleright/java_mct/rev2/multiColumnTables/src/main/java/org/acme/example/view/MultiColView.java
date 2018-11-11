package org.acme.example.view;

import gov.nasa.arc.mct.components.AbstractComponent;
import gov.nasa.arc.mct.components.FeedProvider;
import gov.nasa.arc.mct.components.FeedProvider.FeedType;
import gov.nasa.arc.mct.components.FeedProvider.RenderingInfo;
import gov.nasa.arc.mct.components.TimeConversion;
import gov.nasa.arc.mct.gui.FeedView;
import gov.nasa.arc.mct.gui.FeedView.RenderingCallback;
import gov.nasa.arc.mct.services.component.ViewInfo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

@SuppressWarnings("serial")
public class MultiColView extends FeedView implements RenderingCallback {
	private MultiColTable table;
	private MultiColTableModel model;
	private ViewSettings settings;
	private Map<String, TimeConversion> timeConversionMap = new HashMap<String, TimeConversion>();
	private final AtomicReference<Collection<FeedProvider>> feedProvidersRef = new AtomicReference<Collection<FeedProvider>>(Collections.<FeedProvider>emptyList());
	private boolean receivedData = false;
	private static final DecimalFormat[] formats;
	public static final String HIDDEN_COLUMNS_PROP = "HIDDEN_COLUMNS_PROP";
	private TableControlPanelController controller;
	private TableSettingsControlPanel tableSettingsControlPanel;
	
	static {
		formats = new DecimalFormat[11];
		formats[0] = new DecimalFormat("#");
		String formatString = "#.";
		for (int i = 1; i < formats.length; i++) {
			formatString += "0";
			DecimalFormat format = new DecimalFormat(formatString);
			formats[i] = format;
		}
	}

	public MultiColView(AbstractComponent ac, ViewInfo vi) {
		super(ac,vi);

		JPanel view = new JPanel();
		view.setLayout(new BoxLayout(view, BoxLayout.Y_AXIS));

		// Add the content for this view manifestation.
		AbstractComponent component = getManifestedComponent();
		settings = new ViewSettings();
		table = new MultiColTable(component, settings, this); 
		table.setOpaque(true);
		view.add(table);
		
		model = table.getModel();
		
		setColorsToDefaults();
		model.getJTable().setShowGrid(false);
		model.getJTable().getColumnModel().setColumnMargin(0); //possibly unnecessary
		model.getJTable().setFillsViewportHeight(true); //possibly unnecessary

		//deal with label stuff
		//setLayout(new BorderLayout());
		
		add(view, BorderLayout.NORTH);
		updateFeedProviders(model);
		
		// Initialize controller
		controller = new TableControlPanelController(this, table, model, settings);
		tableSettingsControlPanel = new TableSettingsControlPanel(this, controller, settings);

		// Apply column show/hide states from view properties
		Set<Object> hiddenColIds = getViewProperties().getProperty(HIDDEN_COLUMNS_PROP);
		if (hiddenColIds != null && !hiddenColIds.isEmpty()) {
			List<String> hiddenColIdList = new ArrayList<String>();
			for (Object id : hiddenColIds) {
				controller.removeTableColumn(ColumnType.valueOf((String) id));
				hiddenColIdList.add((String) id);
			}
			tableSettingsControlPanel.updateColumnVisibilityStates(hiddenColIdList);
		}
	}

	private void setColorsToDefaults() {
		Color bg = UIManager.getColor("TableViewManifestation.background");
		setBackground(bg);
		model.getJTable().setBackground(bg);
		bg = UIManager.getColor("TableViewManifestation.foreground");
		model.getJTable().setForeground(bg);
		bg = UIManager.getColor("TableViewManifestation.header.background");
		if(bg!=null) {
			model.getJTable().getTableHeader().setBackground(bg);
		}
		model.getJTable().getTableHeader().setBorder(BorderFactory.createEmptyBorder());
		Color defaultValueColor = UIManager.getColor("TableViewManifestation.defaultValueColor");
		if(defaultValueColor!=null) {
			//renderer....
			model.getJTable().getTableHeader().setForeground(defaultValueColor);
		}
		Color bgSelectionColor = UIManager.getColor("TableViewManifestation.selection.background");
		if(bgSelectionColor!=null) {
			model.getJTable().setSelectionBackground(bgSelectionColor);
		}
		Color fgSelectionColor = UIManager.getColor("TableViewManifestation.selection.foreground");
		if(fgSelectionColor!=null) {
			model.getJTable().setSelectionForeground(fgSelectionColor);
		}
		
	}
	
	/*//copied somewhat blindly from tableViews:
	private MultiColumnSettings getCurrentTableSettings(MultiColTable table) {
		MultiColumnSettings settings = new MultiColumnSettings();
		settings.setColumnWidths(table.getColumnWidths());
		settings.setColumnOrder(table.getColumnOrder());
		settings.setShowGrid(table.getShowGrid());
		settings.setRowHeights(integerToIntArray(table.getRowHeights()));
		settings.setRowHeaderAlignments(table.getRowHeaderAlignments());
		settings.setColumnHeaderAlignments(table
				.getColummnHeaderAlignments());
		settings.setRowFontNames(table.getRowHeaderFontNames());
		settings.setRowFontColors(colorToIntArray(table.getRowHeaderFontColors()));
		settings.setRowHeaderBorderColors(colorToIntArray(table.getRowHeaderBorderColors()));
		settings.setRowBackgroundColors(colorToIntArray(table.getRowHeaderBackgroundColors()));
		settings.setRowFontSizes(integerToIntArray(table.getRowHeaderFontSizes()));
		settings.setRowFontStyles(integerToIntArray(table.getRowHeaderFontStyles()));
		settings.setRowTextAttributes(integerToIntArray(table.getRowHeaderTextAttributes()));
		settings.setColumnFontNames(table.getColumnHeaderFontNames());
		settings.setColumnFontColors(colorToIntArray(table.getColumnHeaderFontColors()));
		settings.setColumnBackgroundColors(colorToIntArray(table.getColumnHeaderBackgroundColors()));
		settings.setColumnHeaderBorderColors(colorToIntArray(table.getColumnHeaderBorderColors()));
		settings.setColumnFontSizes(integerToIntArray(table.getColumnHeaderFontSizes()));
		settings.setColumnFontStyles(integerToIntArray(table.getColumnHeaderFontStyles()));
		settings.setColumnTextAttributes(integerToIntArray(table.getColumnHeaderTextAttributes()));
		settings.setRowHeaderBorderStates(table.getRowHeaderBorderStates());
		settings.setColumnHeaderBorderStates(table.getColumnHeaderBorderStates());

		return settings;
	}*/


	/*private void saveSettingsToPersistence() {
		boolean settingsChanged = false;
		MultiColumnSettings settings = getCurrentTableSettings(table);
		ExtendedProperties viewProperties = getViewProperties();
		for (MultiColumnSettings.AvailableSettings setting : MultiColumnSettings.AvailableSettings
				.values()) {
			String currentValue = getViewProperties().getProperty(
					setting.name(), String.class);
			String newValue = settings.getValue(setting);
			assert (newValue != null) : "Table setting for "
			+ setting.toString() + " has null value";
			if (!newValue.equals(currentValue)) {
				viewProperties.setProperty(setting.name(),
						newValue);
				settingsChanged = true;
			}
		}

		// save all cell settings.
		/*for (int row = 0; row < model.getRowCount(); ++row) {
			for (int col = 0; col < model.getColumnCount(); ++col) {
				AbstractComponent component = (AbstractComponent) model
						.getStoredObjectAt(row, col);
				if (component != null) {
					settingsChanged = (saveCellSettings(component) || settingsChanged);
				}
			}
		}*/
/*
		if (settingsChanged) {
			try {
				updating = true;
				table.updateColumnsHeaderValuesOnly();
				getManifestedComponent().save();
			} finally {
				updating = false;
			}
		}
	}*/

	//useless for multicolumn? prolly not actually //?
	// Creates a formatted JPanel that contains the header in a JLabel
	private JPanel createHeaderRow(String title, Color color, float size) {
		JPanel panel = new JPanel(new BorderLayout());
		JLabel label = new JLabel(title);
		label.setFont(label.getFont().deriveFont(Font.BOLD, size));
		label.setForeground(color);
		panel.add(label, BorderLayout.WEST);
		return panel;
	}

	private int[] colorToIntArray(Color[] a) {
		int[] newArray = new int[a.length];
		for (int i = 0; i < a.length ; i++) {
			newArray[i] = a[i].getRGB();
		}
		return newArray;
	}

	private int[] integerToIntArray(Integer[] a) {
		int[] newArray = new int[a.length];
		for (int i = 0; i < a.length ; i++) {
			newArray[i] = a[i].intValue();
		}
		return newArray;
	}
	
	@Override
	public void render(Map<String, List<Map<String, String>>> data) {
		if (!receivedData) {
			updateFromFeed(data);
		}
	}

	@Override
	public void updateFromFeed(Map<String, List<Map<String, String>>> data) {
		//System.out.println("FFFFFFFFFFFFFFFF updateFromFeed: top"); //debug
		receivedData = true;
		if (data != null) {
			Collection<FeedProvider> feeds = getVisibleFeedProviders();
			for (FeedProvider provider : feeds) {
				String feedId = provider.getSubscriptionId();
				List<Map<String, String>> dataForThisFeed = data
						.get(feedId);
				if (dataForThisFeed != null && !dataForThisFeed.isEmpty()) {
					//System.out.println("FFFFFFFFFFFFFFFF updateFromFeed: dataForThisFeed.isEmpty()==false"); //debug
					// Process the first value for this feed.
					Map<String, String> entry = dataForThisFeed
							.get(dataForThisFeed.size() - 1);
					try {
						//System.out.println("FFFFFFFFFFFFFFFF updateFromFeed: top of try"); //debug
						Object value = entry
								.get(FeedProvider.NORMALIZED_VALUE_KEY);
						RenderingInfo ri = provider.getRenderingInfo(entry);
						MultiColCellSettings settings = new MultiColCellSettings(); //mockup of correct cell settings
						if (provider.getFeedType() != FeedType.STRING) {

							value = executeDecimalFormatter(provider,
									value.toString(), data, settings);
						}
						//System.out.println("FFFFFFFFFFFFFFFF updateFromFeed: right before DisplayedValue stuff"); //debug
						DisplayedValue displayedValue = new DisplayedValue();
						displayedValue.setStatusText(ri.getStatusText());
						displayedValue.setValueColor(ri.getValueColor());
						if (ri.getStatusText().isEmpty() || ri.getStatusText().equals(" ")) {
							if (settings.getFontColor() != null) {
								displayedValue.setValueColor(settings.getFontColor());
							}
						}
						//						Set color according to font color settings, as long as value is valid
						displayedValue.setValue(ri.isValid() ? value
								.toString() : "");
						displayedValue.setNumberOfDecimals(settings
								.getNumberOfDecimals());
						displayedValue.setAlignment(settings.getAlignment());
						model.setValue(provider.getSubscriptionId(),displayedValue);
					} catch (ClassCastException ex) {
						//logger.error("Feed data entry of unexpected type",ex);
					} catch (NumberFormatException ex) {
						/*logger.error("Feed data entry does not contain parsable value",ex);*/
					}
				}
			}
		} else {
			//logger.debug("Data was null");
		}
	}

	/**
	 * Formats decimal places for the given value.
	 * 
	 * @param value
	 *            current value for the cell
	 * @return evaluated value
	 */
	private String executeDecimalFormatter(final FeedProvider provider,
			final String feedValue,
			final Map<String, List<Map<String, String>>> data,
			MultiColCellSettings cellSettings) {
		String rv = feedValue;
		// apply decimal places formatting if appropriate
		FeedType feedType = provider.getFeedType();
		int decimalPlaces = cellSettings.getNumberOfDecimals();
		if (feedType == FeedType.FLOATING_POINT
				|| feedType == FeedType.INTEGER) {
			if (decimalPlaces == -1) {
				decimalPlaces = (feedType == FeedType.FLOATING_POINT) ? MultiColCellSettings.DEFAULT_DECIMALS: 0;
			}
			try {
				rv = formats[decimalPlaces]
						.format(FeedType.FLOATING_POINT
								.convert(feedValue));
			} catch (IllegalFormatException ife) {
				//logger.error("unable to format", ife);
			} catch (NumberFormatException nfe) {
				/*logger.error("unable to convert value to expected feed value",nfe);*/
			}
		}
		return rv;
	}

	@Override
	public void synchronizeTime(Map<String, List<Map<String, String>>> data,
			long syncTime) {
		updateFromFeed(data);
	}

	private void updateFeedProviders(MultiColTableModel model) {
		ArrayList<FeedProvider> feedProviders = new ArrayList<FeedProvider>();
		timeConversionMap.clear();
		for (int rowIndex = 0; rowIndex < model.getRowCount(); ++rowIndex) {
			AbstractComponent component = model.getComponentOfRow(rowIndex);
			if(component!=null) {
				FeedProvider fp = getFeedProvider(component);
				if (fp != null) {
					feedProviders.add(fp);
					TimeConversion tc = component.getCapability(TimeConversion.class);
					if (tc != null) {
						timeConversionMap.put(fp.getSubscriptionId(), tc);
					}							
				}
			}
		}
		feedProviders.trimToSize();
		feedProvidersRef.set(feedProviders);
	}
	
	@Override
	public Collection<FeedProvider> getVisibleFeedProviders() {
		return feedProvidersRef.get();
	}

	@Override
	protected JComponent initializeControlManifestation() {
		return tableSettingsControlPanel;
	}
	
	@Override
	public void updateMonitoredGUI() {
		// Update column visibility states
		
		Set<String> colIdsToBeRemoved = getColumnIdsToBeRemoved();
		if (!colIdsToBeRemoved.isEmpty()) {
			for (String id : colIdsToBeRemoved) {
				controller.removeTableColumn(ColumnType.valueOf(id));
			}
		}

		Set<String> colIdsToBeAdded = getColumnIdsToBeAdded();
		if (!colIdsToBeAdded.isEmpty()) {
			for (String id : colIdsToBeAdded) {
				controller.addTableColumn(ColumnType.valueOf(id));
			}
		}

		Set<Object> hiddenColIds = getViewProperties().getProperty(HIDDEN_COLUMNS_PROP);		
		List<String> hiddenColIdList = new ArrayList<String>();
		for (Object id : hiddenColIds)
			hiddenColIdList.add((String) id);
		tableSettingsControlPanel.updateColumnVisibilityStates(hiddenColIdList);
	}
	
	/**
	 * Returns the column ids to be removed from the current column model. 
	 */
	private Set<String> getColumnIdsToBeRemoved() {
		Set<String> colIdsToBeRemoved = new HashSet<String>();
		Set<Object> hiddenColIds = getViewProperties().getProperty(HIDDEN_COLUMNS_PROP);		
		TableColumnModel columnModel = table.getTable().getColumnModel();
		Enumeration<TableColumn> columns = columnModel.getColumns();
		
		// Get the column ids to hide
		while (columns.hasMoreElements()) {
			TableColumn c = columns.nextElement();
			for (Object hiddenColId : hiddenColIds) {
				if (hiddenColId.equals(c.getIdentifier())) {
					colIdsToBeRemoved.add((String) hiddenColId);
				}
			}
		}
		return colIdsToBeRemoved;
	}
	
	/**
	 * Returns the column ids to be added to the current column model. 
	 */
	private Set<String> getColumnIdsToBeAdded() {
		Set<String> colIdsToBeAdded = new HashSet<String>();
		
		// Get the set of column ids that are visible 
		Set<Object> hiddenColIds = getViewProperties().getProperty(HIDDEN_COLUMNS_PROP);
		for (ColumnType type : ColumnType.values()) {
			boolean found = false;
			for (Object id : hiddenColIds) {
				if (id.equals(type.name()))
					found = true;
			}
			if (!found)
				colIdsToBeAdded.add(type.name());
		}
		
		// Remove the column ids that are already visible
		TableColumnModel columnModel = table.getTable().getColumnModel();
		Enumeration<TableColumn> columns = columnModel.getColumns();
		while (columns.hasMoreElements()) {
			TableColumn c = columns.nextElement();
			if (colIdsToBeAdded.contains(c.getIdentifier()))
				colIdsToBeAdded.remove(c.getIdentifier());
		}
		
		return colIdsToBeAdded;		
	}
}
