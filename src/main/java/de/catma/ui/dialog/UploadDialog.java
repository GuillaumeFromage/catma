/*   
 *   CATMA Computer Aided Text Markup and Analysis
 *   
 *   Copyright (C) 2009-2013  University Of Hamburg
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.catma.ui.dialog;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.text.MessageFormat;

import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.v7.ui.HorizontalLayout;
import com.vaadin.v7.ui.ProgressIndicator;
import com.vaadin.ui.UI;
import com.vaadin.v7.ui.Upload;
import com.vaadin.v7.ui.Upload.FailedEvent;
import com.vaadin.v7.ui.Upload.Receiver;
import com.vaadin.v7.ui.Upload.StartedEvent;
import com.vaadin.v7.ui.Upload.SucceededEvent;
import com.vaadin.v7.ui.VerticalLayout;
import com.vaadin.ui.Window;

public class UploadDialog extends VerticalLayout {
	
	private static class DataReceiver implements Receiver {
		private ByteArrayOutputStream data;
		
		public DataReceiver(ByteArrayOutputStream data) {
			this.data = data;
		}

		public OutputStream receiveUpload(String filename, String mimeType) {
			return data;
		}
	}

	
	private Window dialogWindow;
	private String caption;
	private SaveCancelListener<byte[]> saveCancelListener;
	private Upload upload;
	private ProgressIndicator pi;
	private ByteArrayOutputStream data;
	private Button btCancel;
	
	public UploadDialog(String caption, SaveCancelListener<byte[]> saveCancelListener) {
		this.caption = caption;
		this.saveCancelListener = saveCancelListener;
		this.data = new ByteArrayOutputStream();

		initComponents();
		initActions();
	}
	
	private void initActions() {
		 upload.addStartedListener(new Upload.StartedListener() {
            public void uploadStarted(StartedEvent event) {
//	                upload.setVisible(false);
            	pi.setVisible(true);
                pi.setValue(0f);
                pi.setPollingInterval(500);
                upload.setCaption(
                		MessageFormat.format(Messages.getString("UploadDialog.uploadingFile"), event.getFilename())); //$NON-NLS-1$
            }
        });

        upload.addProgressListener(new Upload.ProgressListener() {
            public void updateProgress(long readBytes, long contentLength) {
                pi.setValue(new Float(readBytes / (float) contentLength));
            }

        });

        upload.addSucceededListener(new Upload.SucceededListener() {
            public void uploadSucceeded(SucceededEvent event) {
            	upload.setCaption(
            			MessageFormat.format(Messages.getString("UploadDialog.uploadingFileSuccess"), event.getFilename())); //$NON-NLS-1$

            	saveCancelListener.savePressed(data.toByteArray());
            	upload.removeSucceededListener(this);
            	UploadDialog.this.close();
            }
        });

        upload.addFailedListener(new Upload.FailedListener() {
            public void uploadFailed(FailedEvent event) {
            	upload.setCaption(Messages.getString("UploadDialog.uploadingInterrupted")); //$NON-NLS-1$
            }
        });
        btCancel.addClickListener(new ClickListener() {
        	
        	public void buttonClick(ClickEvent event) {
        		upload.interruptUpload();
        		UI.getCurrent().removeWindow(dialogWindow);
        		saveCancelListener.cancelPressed();
        	}
        });
	}

	private void close() {
		UI.getCurrent().removeWindow(dialogWindow);
	}

	private void initComponents() {
		setSizeFull();
		setSpacing(true);
		VerticalLayout content = new VerticalLayout();
		content.setMargin(true);
		
		dialogWindow = new Window(caption, content);
		dialogWindow.setModal(true);
		
		upload = new Upload(
				Messages.getString("UploadDialog.selectFileHint"), //$NON-NLS-1$
				new DataReceiver(data));
		upload.setButtonCaption(Messages.getString("UploadDialog.selectFile")); //$NON-NLS-1$
		upload.setImmediate(true);
		
		addComponent(upload);
		pi = new ProgressIndicator();
		pi.setVisible(false);
		addComponent(pi);
		
		HorizontalLayout buttonPanel = new HorizontalLayout();
		buttonPanel.setSpacing(true);

		btCancel = new Button(Messages.getString("UploadDialog.Cancel")); //$NON-NLS-1$
		buttonPanel.addComponent(btCancel);
		
		addComponent(buttonPanel);
		this.setComponentAlignment(buttonPanel, Alignment.BOTTOM_RIGHT);
		
		content.addComponent(this);
		
		upload.focus();

	}
	
	public void show(String dialogWidth) {
		dialogWindow.setWidth(dialogWidth);
		UI.getCurrent().addWindow(dialogWindow);
	}
	
	public void show() {
		show("25%"); //$NON-NLS-1$
	}
	
	
}