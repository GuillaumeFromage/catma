package de.catma.ui.repository;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vaadin.Application;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.terminal.ClassResource;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.HorizontalSplitPanel;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.VerticalSplitPanel;

import de.catma.CatmaApplication;
import de.catma.document.Corpus;
import de.catma.document.repository.Repository;
import de.catma.ui.tabbedview.ClosableTab;


public class RepositoryView extends VerticalLayout implements ClosableTab {
	
	private Logger logger = Logger.getLogger(this.getClass().getName());
	private Repository repository;
	private PropertyChangeListener exceptionOccurredListener;
	private SourceDocumentPanel sourceDocumentPanel;
	private CorpusPanel corpusPanel;
	private TagLibraryPanel tagLibraryPanel;
	private boolean init = false;
	private Application application;
	
	public RepositoryView(Repository repository) {
		this.repository = repository;

		exceptionOccurredListener = new PropertyChangeListener() {
			
			public void propertyChange(PropertyChangeEvent evt) {
				if (application !=null) {
					((CatmaApplication)application).showAndLogError(
						"Repository Error!", (Throwable)evt.getNewValue());
				}
				else {
					logger.log(
						Level.SEVERE, "repository error", 
						(Throwable)evt.getNewValue());
				}
			}
		};
		
		
	}

	@Override
	public void attach() {
		super.attach();
		if (!init) {
			initComponents();
			this.application = getApplication();
			this.repository.addPropertyChangeListener(
					Repository.RepositoryChangeEvent.exceptionOccurred, 
					exceptionOccurredListener);
			init = true;
		}
		
	}

	private void initComponents() {
		setSizeFull();
		this.setMargin(false, true, true, true);
		this.setSpacing(true);
		
		Component documentsLabel = createDocumentsLabel();
		addComponent(documentsLabel);
		VerticalSplitPanel splitPanel = new VerticalSplitPanel();
		splitPanel.setSplitPosition(65);
		
		Component documentsManagerPanel = createDocumentsManagerPanel();
		splitPanel.addComponent(documentsManagerPanel);
		
		tagLibraryPanel = new TagLibraryPanel(
				repository.getTagManager(), repository);
		splitPanel.addComponent(tagLibraryPanel);
		
		addComponent(splitPanel);
		setExpandRatio(splitPanel, 1f);
	}

	

	private Component createDocumentsManagerPanel() {
		
		HorizontalSplitPanel documentsManagerPanel = new HorizontalSplitPanel();
		documentsManagerPanel.setSplitPosition(25);
		documentsManagerPanel.setSizeFull();
		
		sourceDocumentPanel = new SourceDocumentPanel(repository);
		
		corpusPanel = new CorpusPanel(repository, new ValueChangeListener() {
			
			public void valueChange(ValueChangeEvent event) {
				Object value = event.getProperty().getValue();
				sourceDocumentPanel.setSourceDocumentsFilter((Corpus)value);
			}		
		});

		documentsManagerPanel.addComponent(corpusPanel);
		documentsManagerPanel.addComponent(sourceDocumentPanel);
		
		return documentsManagerPanel;
	}

	private Component createDocumentsLabel() {
		HorizontalLayout labelLayout = new HorizontalLayout();
		labelLayout.setWidth("100%");
		
		Label documentsLabel = new Label("Document Manager");
		documentsLabel.addStyleName("bold-label");
		
		labelLayout.addComponent(documentsLabel);
		
		Label helpLabel = new Label();
		helpLabel.setIcon(new ClassResource(
				"ui/resources/icon-help.gif", 
				getApplication()));
		helpLabel.setWidth("20px");
		helpLabel.setDescription(
				"<h3>Hints</h3>" +
				"<h4>First steps</h4>" +
				"<h5>Adding a Source Document</h5>" +
				"You can add a Source Document by clicking the \"Add Source Document\"-button. " +
				"A Source Document can be a web resource pointed to by the URL or you can upload a document from your computer. " +
				"<h5>Tagging a Source Document</h5>" +
				"When you add your first Source Document, CATMA generates a set of example items to get you going: " +
				"<ul><li>A User Markup Collection to hold your markup</li><li>A Tag Library with an example Tagset that contains an example Tag</li></ul> "+
				"To start tagging a Source Document, just select the example User Markup Collection from the tree and click the \"Open User Markup Collection\"-button. " +
				"Then follow the instructions given to you by the Tagger component." +
				"<h5>Analyze a Source Document</h5>" +
				"To analyze a Source Document, just select that document from the tree and click \"Analyze Source Document\" in the \"More Actions\"-menu." +
				"Then follow the instructions given to you by the Analyzer component.");

		labelLayout.addComponent(helpLabel);
		labelLayout.setComponentAlignment(helpLabel, Alignment.MIDDLE_RIGHT);
		
		return labelLayout;
	}

	public Repository getRepository() {
		return repository;
	}
	
	public void close() {
		this.repository.removePropertyChangeListener(
				Repository.RepositoryChangeEvent.exceptionOccurred, 
				exceptionOccurredListener);
		
		this.corpusPanel.close();
		this.sourceDocumentPanel.close();
		this.tagLibraryPanel.close();
		
		// repository is closed by the RepositoryManager from RepositoryManagerView
	}
	
	public void addClickshortCuts() { /* noop*/	}
	
	public void removeClickshortCuts() { /* noop*/ }

}


