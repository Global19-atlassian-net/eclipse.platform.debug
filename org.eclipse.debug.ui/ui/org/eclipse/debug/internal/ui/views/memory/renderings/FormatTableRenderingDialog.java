/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.memory.renderings;

import org.eclipse.debug.internal.ui.DebugUIMessages;
import org.eclipse.debug.internal.ui.preferences.IDebugPreferenceConstants;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.memory.AbstractTableRendering;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.PageBook;

public class FormatTableRenderingDialog extends Dialog
	{

		private int[] fColumnSizes = new int[] {1, 2, 4, 8, 16};
		private int[] fRowSizes = new int[] {1, 2, 4, 8, 16};
		private Combo fColumnControl;
		private Combo fRowControl;
		
		private int fCurrentColIdx = -1;
		private int fCurrentRowIdx = -1;
		private Control fPreivewPage;
		private PageBook fPreviewPageBook;
		private Button fDefaultButton;
		private Text fDefaultColValue;
		private Text fDefaultRowValue;
		private AbstractTableRendering fRendering;
		private int fColumnSize;
		private int fRowSize;
		private Label fMsgLabel;
		private boolean fDisableCancel = false;
		private String fMsg;
		

		public FormatTableRenderingDialog(AbstractTableRendering rendering, Shell parentShell) {
			super(parentShell);
			setShellStyle(getShellStyle() | SWT.RESIZE);
			fRendering = rendering;
			fMsg = DebugUIMessages.FormatTableRenderingAction_1;
		}
		
		protected Control createDialogArea(Composite parent) {
			getShell().setText(DebugUIMessages.FormatTableRenderingAction_0);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getShell(), IDebugUIConstants.PLUGIN_ID + ".FormatTableRenderingDialog_context"); //$NON-NLS-1$
			
			Composite composite = new Composite(parent, SWT.NONE);
			GridLayout layout = new GridLayout();
			layout.numColumns = 3;
			layout.makeColumnsEqualWidth = false;
			composite.setLayout(layout);
			GridData data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = SWT.FILL;
			data.verticalAlignment = SWT.FILL;
			data.widthHint = 300;
			composite.setLayoutData(data);
			
			fMsgLabel = new Label(composite, SWT.NONE);
			fMsgLabel.setText(fMsg);
			
			data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.horizontalAlignment = SWT.BEGINNING;
			data.horizontalSpan = 3;
			fMsgLabel.setLayoutData(data);
			
			Label rowLabel = new Label(composite, SWT.NONE);
			rowLabel.setText(DebugUIMessages.FormatTableRenderingAction_2);
			fRowControl = new Combo(composite, SWT.READ_ONLY);
			for (int i=0; i<fRowSizes.length; i++)
			{
				fRowControl.add(String.valueOf(fRowSizes[i]));
			}
			
			fRowControl.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					if (fCurrentRowIdx != fRowControl.getSelectionIndex())
					{
						fCurrentRowIdx = fRowControl.getSelectionIndex();
						refreshPreviewPage();
						updateButtons();
					}
				}

				public void widgetDefaultSelected(SelectionEvent e) {
				}});
			
			data = new GridData();
			data.grabExcessHorizontalSpace = false;
			data.horizontalAlignment = SWT.BEGINNING;
			fRowControl.setLayoutData(data);
			
			Label unit = new Label(composite, SWT.NONE);
			unit.setText(DebugUIMessages.FormatTableRenderingAction_3);		
			
			Label columnLabel = new Label(composite, SWT.NONE);
			columnLabel.setText(DebugUIMessages.FormatTableRenderingAction_4);
			fColumnControl = new Combo(composite, SWT.READ_ONLY);
			for (int i=0; i<fColumnSizes.length; i++)
			{
				fColumnControl.add(String.valueOf(fColumnSizes[i]));
			}
			
			fColumnControl.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					if (fCurrentColIdx != fColumnControl.getSelectionIndex())
					{
						fCurrentColIdx = fColumnControl.getSelectionIndex();
						refreshPreviewPage();
						updateButtons();
					}
				}

				public void widgetDefaultSelected(SelectionEvent e) {
				}});
			
			data = new GridData();
			data.grabExcessHorizontalSpace = false;
			data.horizontalAlignment = SWT.BEGINNING;
			fColumnControl.setLayoutData(data);
			
			unit = new Label(composite, SWT.NONE);
			unit.setText(DebugUIMessages.FormatTableRenderingAction_5);
			
			populateDialog();
			
			fDefaultButton = new Button(composite, SWT.NONE);
			fDefaultButton.setText(DebugUIMessages.FormatTableRenderingAction_8);
			data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = SWT.END;
			data.horizontalSpan = 3;
			fDefaultButton.setLayoutData(data);
			
			fDefaultButton.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					saveDefaults();
				}

				public void widgetDefaultSelected(SelectionEvent e) {
					
				}});
			
			Group group = new Group(composite, SWT.NONE);
			group.setText(DebugUIMessages.FormatTableRenderingAction_7);
			group.setLayout(new GridLayout());
			data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = SWT.FILL;
			data.verticalAlignment = SWT.FILL;
			data.horizontalSpan = 3;
			group.setLayoutData(data);
			
			fPreviewPageBook = new PageBook(group, SWT.NONE);
			data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = SWT.FILL;
			data.verticalAlignment = SWT.FILL;
			fPreviewPageBook.setLayoutData(data);
			
			int rowSize = fRowSizes[fRowControl.getSelectionIndex()];
			int colSize = fColumnSizes[fColumnControl.getSelectionIndex()];

			fPreivewPage = createPreviewPage(fPreviewPageBook, rowSize, colSize);
			fPreviewPageBook.showPage(fPreivewPage);
			
			Label defaultRow = new Label(composite, SWT.NONE);
			defaultRow.setText(DebugUIMessages.FormatTableRenderingDialog_0);
			fDefaultRowValue = new Text(composite, SWT.READ_ONLY);
			int defRow = getDefaultRowSize(fRendering.getMemoryBlock().getModelIdentifier());
			fDefaultRowValue.setText(String.valueOf(defRow));
			Label def = new Label(composite, SWT.NONE);
			def.setText(DebugUIMessages.FormatTableRenderingDialog_1);
			
			data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = SWT.BEGINNING;
			def.setLayoutData(data);
			
			
			Label defaultCol = new Label(composite, SWT.NONE);
			defaultCol.setText(DebugUIMessages.FormatTableRenderingDialog_2);
			fDefaultColValue = new Text(composite, SWT.READ_ONLY);
			int defCol = getDefaultColumnSize(fRendering.getMemoryBlock().getModelIdentifier());
			fDefaultColValue.setText(String.valueOf(defCol));
			def = new Label(composite, SWT.NONE);
			def.setText(DebugUIMessages.FormatTableRenderingDialog_3);
			
			data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.grabExcessVerticalSpace = true;
			data.horizontalAlignment = SWT.BEGINNING;
			def.setLayoutData(data);
			
			Button restoreButton = new Button(composite, SWT.NONE);
			restoreButton.setText(DebugUIMessages.FormatTableRenderingAction_6);
			restoreButton.addSelectionListener(new SelectionListener() {
				public void widgetSelected(SelectionEvent e) {
					restoreDefaults();
				}
				public void widgetDefaultSelected(SelectionEvent e) {
				}});
			data = new GridData();
			data.grabExcessHorizontalSpace = true;
			data.horizontalAlignment = SWT.END;
			data.horizontalSpan = 3;
			data.verticalAlignment = SWT.CENTER;
			restoreButton.setLayoutData(data);
			
			return composite;
		}

		protected void okPressed() {
			int idx = fColumnControl.getSelectionIndex();
			fColumnSize = fColumnSizes[idx];
			fRowSize = fRowSizes[fRowControl.getSelectionIndex()];
			super.okPressed();
		}
		
		void populateDialog()
		{
			int currentColSize = fRendering.getAddressableUnitPerColumn();
			int currentRowSize = fRendering.getAddressableUnitPerLine();
			setCurrentRowColSizes(currentRowSize, currentColSize);
		}

		int populateControl(int currentSize, int[] searchArray, Combo control) {
			int idx = 0;
			for (int i=0 ;i<searchArray.length; i++)
			{
				if (searchArray[i] == currentSize)
				{
					idx = i;
					break;
				}
			}
			control.select(idx);
			return idx;
		}
		
		Control createPreviewPage(Composite parent, int rowSize, int colSize)
		{			
			if (!isValid(rowSize, colSize))
			{	
				Label label = new Label(parent, SWT.NONE);
				StringBuffer errorMsg = new StringBuffer();
				errorMsg.append(DebugUIMessages.FormatTableRenderingAction_9);
				errorMsg.append("\n"); //$NON-NLS-1$
				errorMsg.append(DebugUIMessages.FormatTableRenderingAction_11);
				
				if (colSize > rowSize)
				{
					errorMsg.append("\n"); //$NON-NLS-1$
					errorMsg.append(DebugUIMessages.FormatTableRenderingAction_13);
				}
				
				label.setText(errorMsg.toString());
				
				return label;
			}
			
			Table table = new Table(parent, SWT.BORDER);
			table.setHeaderVisible(true);
			
			int numCol = rowSize/colSize;
			
			TableColumn addressCol = new TableColumn(table, SWT.NONE);
			
			TableColumn[] columns = new TableColumn[numCol];
			for (int i=0; i<columns.length; i++)
			{
				columns[i] = new TableColumn(table, SWT.NONE);
			}
			
			StringBuffer buf = new StringBuffer();
			for (int j=0; j<colSize; j++)
			{
				buf.append("X"); //$NON-NLS-1$
			}
			
			for (int i = 0; i < 4; i++) {
				TableItem tableItem = new TableItem(table, SWT.NONE);
				
				String[] text = new String[numCol + 1];
				text[0] = DebugUIMessages.FormatTableRenderingAction_15;
				for (int j=1; j<text.length; j++)
				{
					text[j] = buf.toString(); 
				}
				
				tableItem.setText(text);
			}
			
			addressCol.pack();
			for (int i=0; i<columns.length; i++)
			{
				columns[i].pack();
			}
			
			
			return table;
		}
		
		boolean isValid(int rowSize, int colSize)
		{
			if (rowSize % colSize != 0)
				return false;
			
			if (colSize > rowSize)
				return false;
			
			return true;
		}

		void refreshPreviewPage() {
			fPreivewPage.dispose();
			
			int rowSize = fRowSizes[fRowControl.getSelectionIndex()];
			int colSize = fColumnSizes[fColumnControl.getSelectionIndex()];
			fPreivewPage = createPreviewPage(fPreviewPageBook, rowSize, colSize);
			fPreviewPageBook.showPage(fPreivewPage);
		}

		void updateButtons() {
			int rowSize = fRowSizes[fRowControl.getSelectionIndex()];
			int colSize = fColumnSizes[fColumnControl.getSelectionIndex()];
			Button button = getButton(IDialogConstants.OK_ID);
			if (!isValid(rowSize, colSize))
			{
				button.setEnabled(false);
				fDefaultButton.setEnabled(false);
				
				
			}
			else
			{
				button.setEnabled(true);
				fDefaultButton.setEnabled(true);
			}
		}

		String getRowPrefId(String modelId) {
			String rowPrefId = IDebugPreferenceConstants.PREF_ROW_SIZE + ":" + modelId; //$NON-NLS-1$
			return rowPrefId;
		}

		String getColumnPrefId(String modelId) {
			String colPrefId = IDebugPreferenceConstants.PREF_COLUMN_SIZE + ":" + modelId; //$NON-NLS-1$
			return colPrefId;
		}
		
		int getDefaultRowSize(String modelId)
		{
			int row = DebugUITools.getPreferenceStore().getInt(getRowPrefId(modelId));
			if (row == 0)
			{
				DebugUITools.getPreferenceStore().setValue(getRowPrefId(modelId), IDebugPreferenceConstants.PREF_ROW_SIZE_DEFAULT);
			}
			
			row = DebugUITools.getPreferenceStore().getInt(getRowPrefId(modelId));
			return row;
			
		}
		
		int getDefaultColumnSize(String modelId)
		{
			int col = DebugUITools.getPreferenceStore().getInt(getColumnPrefId(modelId));
			if (col == 0)
			{
				DebugUITools.getPreferenceStore().setValue(getColumnPrefId(modelId), IDebugPreferenceConstants.PREF_COLUMN_SIZE_DEFAULT);
			}
			
			col = DebugUITools.getPreferenceStore().getInt(getColumnPrefId(modelId));
			return col;
		}

		void saveDefaults() {
			int columnSize = fColumnSizes[fColumnControl.getSelectionIndex()];
			int rowSize = fRowSizes[fRowControl.getSelectionIndex()];
			
			fDefaultColValue.setText(String.valueOf(columnSize));
			fDefaultRowValue.setText(String.valueOf(rowSize));
			
			// save preference
			// find model id
			String modelId = fRendering.getMemoryBlock().getModelIdentifier();
			
			// constrcut preference id
			String rowPrefId = getRowPrefId(modelId);
			String colPrefId = getColumnPrefId(modelId);
			
			// save setting
			IPreferenceStore prefStore = DebugUITools.getPreferenceStore();
			prefStore.setValue(rowPrefId, rowSize);
			prefStore.setValue(colPrefId, columnSize);
		}

		void restoreDefaults() {
			String modelId = fRendering.getMemoryBlock().getModelIdentifier();
			int defaultRowSize = DebugUITools.getPreferenceStore().getInt(getRowPrefId(modelId));
			int defaultColSize = DebugUITools.getPreferenceStore().getInt(getColumnPrefId(modelId));
			
			if (defaultRowSize == 0 || defaultColSize == 0)
			{
				defaultRowSize = DebugUITools.getPreferenceStore().getInt(IDebugPreferenceConstants.PREF_ROW_SIZE);
				defaultColSize = DebugUITools.getPreferenceStore().getInt(IDebugPreferenceConstants.PREF_COLUMN_SIZE);
				
				DebugUITools.getPreferenceStore().setValue(getRowPrefId(modelId), defaultRowSize);
				DebugUITools.getPreferenceStore().setValue(getColumnPrefId(modelId), defaultColSize);
			}
			
			populateControl(defaultRowSize, fRowSizes, fRowControl);
			populateControl(defaultColSize, fColumnSizes, fColumnControl);
			
			fCurrentRowIdx = fRowControl.getSelectionIndex();
			fCurrentColIdx = fColumnControl.getSelectionIndex();
			
			refreshPreviewPage();
			updateButtons();
		}

		public int getColumnSize() {
			return fColumnSize;
		}

		public int getRowSize() {
			return fRowSize;
		}
		
		public void setCurrentRowColSizes(int row, int col)
		{
			fCurrentColIdx = populateControl(col, fColumnSizes, fColumnControl);
			fCurrentRowIdx = populateControl(row, fRowSizes, fRowControl);
		}

		protected Control createButtonBar(Composite parent) {
			Control ret =  super.createButtonBar(parent);
			if (fDisableCancel)
				getButton(IDialogConstants.CANCEL_ID).setEnabled(false);
			updateButtons();
			return ret;
		}
		
		public void openError(String msg)
		{
			fDisableCancel = true;
			fMsg = msg;
			open();
		}
	}