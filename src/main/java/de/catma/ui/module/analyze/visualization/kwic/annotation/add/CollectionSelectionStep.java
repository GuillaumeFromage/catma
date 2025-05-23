package de.catma.ui.module.analyze.visualization.kwic.annotation.add;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.vaadin.contextmenu.ContextMenu;
import com.vaadin.data.TreeData;
import com.vaadin.data.provider.HierarchicalQuery;
import com.vaadin.data.provider.TreeDataProvider;
import com.vaadin.server.SerializablePredicate;
import com.vaadin.ui.Grid.SelectionMode;
import com.vaadin.ui.Label;
import com.vaadin.ui.MenuBar.MenuItem;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.TreeGrid;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.renderers.HtmlRenderer;

import de.catma.document.annotation.AnnotationCollectionReference;
import de.catma.document.source.SourceDocumentReference;
import de.catma.project.Project;
import de.catma.project.event.ChangeType;
import de.catma.project.event.CollectionChangeEvent;
import de.catma.ui.component.TreeGridFactory;
import de.catma.ui.component.actiongrid.ActionGridComponent;
import de.catma.ui.dialog.SaveCancelListener;
import de.catma.ui.dialog.SingleTextInputDialog;
import de.catma.ui.dialog.wizard.ProgressStep;
import de.catma.ui.dialog.wizard.ProgressStepFactory;
import de.catma.ui.dialog.wizard.StepChangeListener;
import de.catma.ui.dialog.wizard.WizardContext;
import de.catma.ui.dialog.wizard.WizardStep;
import de.catma.ui.module.main.ErrorHandler;
import de.catma.ui.module.project.CollectionResource;
import de.catma.ui.module.project.DocumentResource;
import de.catma.ui.module.project.Resource;
import de.catma.user.Member;

class CollectionSelectionStep extends VerticalLayout implements WizardStep {
	private enum DocumentGridColumn {
		NAME,
		RESPONSIBLE,
	}

	private ProgressStep progressStep;
    private TreeGrid<Resource> documentGrid;
    private ActionGridComponent<TreeGrid<Resource>> documentGridComponent;
	private Project project;
	private TreeData<Resource> documentData;
	private WizardContext context;
	private TreeDataProvider<Resource> documentDataProvider;
	private StepChangeListener stepChangeListener;
	private EventBus eventBus;
	private MenuItem miToggleResponsibiltityFilter;
    
	public CollectionSelectionStep(EventBus eventBus, Project project, WizardContext context, ProgressStepFactory progressStepFactory) {
		this.eventBus = eventBus;
		this.project = project;
		this.context = context;
		this.progressStep = progressStepFactory.create(3, "Select Collections");
		initComponents();
		initActions();
		try {
			initData();
		} catch (Exception e) {
			((ErrorHandler) UI.getCurrent()).showAndLogError("Error loading available collections", e);
		}
	}
	
	@Subscribe
	public void handleCollectionChanged(CollectionChangeEvent collectionChangeEvent) {
		if (collectionChangeEvent.getChangeType().equals(ChangeType.CREATED)) {
    		AnnotationCollectionReference collectionReference = 
    				collectionChangeEvent.getCollectionReference();
			addCollection(collectionReference);
		}
	}	

	private void addCollection(AnnotationCollectionReference annotationCollectionRef) {
		documentData.getRootItems().stream()
				.filter(resource -> ((DocumentResource) resource).getSourceDocumentRef().getUuid().equals(annotationCollectionRef.getSourceDocumentId()))
				.findAny()
				.ifPresent(documentResource -> documentData.addItem(
						documentResource,
						new CollectionResource(
								annotationCollectionRef,
								project.getId(),
								project.getCurrentUser()
						)
				));
		documentDataProvider.refreshAll();
	}

	private void initData() throws Exception {
    	Map<String, Member> membersByIdentfier = project.getProjectMembers().stream()
			.collect(Collectors.toMap(
					Member::getIdentifier, 
					Function.identity()));

        documentData = new TreeData<>();
        
        @SuppressWarnings("unchecked")
		Set<String> documentIds = (Set<String>) context.get(AnnotationWizardContextKey.DOCUMENTIDS);
        
        for(String documentId : documentIds) {
        	SourceDocumentReference srcDoc = project.getSourceDocumentReference(documentId);
            DocumentResource docResource = 
            		new DocumentResource(
            			srcDoc, 
            			project.getId(),
            			srcDoc.getResponsibleUser()!= null?membersByIdentfier.get(srcDoc.getResponsibleUser()):null);
            
            documentData.addItem(null,docResource);
            
            List<AnnotationCollectionReference> collections = 
            		srcDoc.getUserMarkupCollectionRefs();
            
        	List<Resource> collectionResources = collections
    		.stream()
    		.filter(collectionRef -> 
    			!miToggleResponsibiltityFilter.isChecked() 
    			|| collectionRef.isResponsible(project.getCurrentUser().getIdentifier()))
    		.map(collectionRef -> 
    			(Resource)new CollectionResource(
    				collectionRef, 
    				project.getId(),
    				collectionRef.getResponsibleUser()!= null?membersByIdentfier.get(collectionRef.getResponsibleUser()):null)
    		)
    		.collect(Collectors.toList());
    		
            
            if(!collections.isEmpty()){
            	
                documentData.addItems(
                	docResource,
                	collectionResources
                );
            }
            else {
            	Notification.show(
            		"Info", 
            		String.format("There is no collection yet for document \"%s\"", srcDoc),
            		Type.HUMANIZED_MESSAGE);
            }
        }
        
        documentDataProvider = new TreeDataProvider<Resource>(documentData);
        documentGrid.setDataProvider(documentDataProvider);
	}

	private void initActions() {
		documentGridComponent.setSearchFilterProvider(searchInput -> createSearchFilter(searchInput));
		documentGridComponent.getActionGridBar().addBtnAddClickListener(clickEvent -> handleAddCollectionRequest());
        ContextMenu documentsGridMoreOptionsContextMenu = 
            	documentGridComponent.getActionGridBar().getBtnMoreOptionsContextMenu();

        documentsGridMoreOptionsContextMenu.addItem(
        	"Select Filtered Documents", mi-> handleSelectFilteredDocuments());
        documentsGridMoreOptionsContextMenu.addItem(
        	"Select Filtered Collections", mi-> handleSelectFilteredCollections());
        
        documentGrid.addSelectionListener(event -> {
        	if (stepChangeListener != null) {
        		stepChangeListener.stepChanged(this);
        	}
        });
	}

	private SerializablePredicate<Object> createSearchFilter(String searchInput) {
		return new SerializablePredicate<Object>() {
			@Override
			public boolean test(Object r) {
				if (r instanceof CollectionResource) {
					return r.toString().toLowerCase().contains(searchInput.toLowerCase());
				}
				else {
					if (r.toString().toLowerCase().contains(searchInput.toLowerCase())) {
						return true;
					}
					else {
						return documentData.getChildren((Resource)r)
								.stream()
								.filter(child -> child.toString().toLowerCase().startsWith(searchInput.toLowerCase()))
								.findAny()
								.isPresent();
					}
				}
			}
		};
	}

	private void handleSelectFilteredDocuments() {
		documentDataProvider.fetch(
				new HierarchicalQuery<>(documentDataProvider.getFilter(), null))
		.forEach(resource -> documentGrid.select(resource));
	}

	private void handleSelectFilteredCollections() {
		documentDataProvider.fetch(
				new HierarchicalQuery<>(documentDataProvider.getFilter(), null))
		.forEach(resource -> {
			documentDataProvider.fetch(new HierarchicalQuery<>(documentDataProvider.getFilter(), resource))
			.forEach(child -> documentGrid.select(child));
		});
	}

	private void handleAddCollectionRequest() {
		try {
			@SuppressWarnings("unchecked")
			TreeDataProvider<Resource> resourceDataProvider = (TreeDataProvider<Resource>) documentGrid.getDataProvider();

			Set<Resource> selectedResources = documentGrid.getSelectedItems();

			Set<SourceDocumentReference> selectedSourceDocumentRefs = new HashSet<>();

			for (Resource resource : selectedResources) {
				Resource root = resourceDataProvider.getTreeData().getParent(resource);
				if (root == null) {
					root = resource;
				}

				DocumentResource documentResource = (DocumentResource) root;
				selectedSourceDocumentRefs.add(documentResource.getSourceDocumentRef());
			}

			if (selectedSourceDocumentRefs.isEmpty()) {
				Notification.show("Info", "Please select one or more documents first!", Type.HUMANIZED_MESSAGE);
				return;
			}

			SingleTextInputDialog collectionNameDlg = new SingleTextInputDialog(
					"Create Annotation Collection(s)",
					"Please enter the collection name:",
					new SaveCancelListener<String>() {
						@Override
						public void savePressed(String collectionName) {
							for (SourceDocumentReference sourceDocumentRef : selectedSourceDocumentRefs) {
								project.createAnnotationCollection(collectionName, sourceDocumentRef);
							}
						}
					}
			);
			collectionNameDlg.show();
		}
		catch (Exception e) {
			((ErrorHandler) UI.getCurrent()).showAndLogError("Failed to create annotation collection", e);
		}
	}

	private void initComponents() {
		setSizeFull();
    	documentGrid = TreeGridFactory.createDefaultTreeGrid();
    	documentGrid.setSizeFull();
    	
        documentGrid.addStyleNames(
				"flat-undecorated-icon-buttonrenderer");

        // disabled, custom height needed to display additional document details but causes a styling issue, see below
//        documentGrid.setRowHeight(45);
        documentGrid.setSelectionMode(SelectionMode.MULTI);
        
		documentGrid
			.addColumn(resource -> resource.getIcon(), new HtmlRenderer())
			.setWidth(100);
        
		Function<Resource,String> buildNameFunction = (resource) -> {
			StringBuilder sb = new StringBuilder()
			  .append("<div class='documentsgrid__doc'> ")
		      .append("<div class='documentsgrid__doc__title'> ")
		      .append(resource.getName())
		      .append("</div>");
			// disabled due to styling issue and not really adding value, also see above and `div.documentsgrid__doc` in CSS
			// if re-enabling we need to set `height: 100%` on `.catma .v-treegrid:not(.borderless) .v-treegrid-header::after` so that the box-shadow does not
			// disappear from the header (but this hasn't been tested properly)
//			if(resource.hasDetail()){
//		        sb
//		        .append("<span class='documentsgrid__doc__author'> ")
//		        .append(resource.getDetail())
//		        .append("</span>");
//			}
			sb.append("</div>");
				        
		    return sb.toString();
		};
		
		Function<Resource,String> buildResponsibleFunction = (resource) -> {
			
			if (resource.getResponsibleUser() == null) {
				return "";
			}
			
			StringBuilder sb = new StringBuilder()
			  .append("<div class='documentsgrid__doc'> ") //$NON-NLS-1$
		      .append(resource.getResponsibleUser())
		      .append("</div>"); //$NON-NLS-1$
			sb.append("</div>"); //$NON-NLS-1$
				        
		    return sb.toString();
		};
		
        documentGrid
        	.addColumn(resource -> buildNameFunction.apply(resource), new HtmlRenderer())  	
        	.setCaption("Name")
        	.setId(DocumentGridColumn.NAME.name());
        	
        documentGrid
		  	.addColumn(res -> buildResponsibleFunction.apply(res), new HtmlRenderer())
		  	.setCaption("Responsible")
		  	.setId(DocumentGridColumn.RESPONSIBLE.name())
		  	.setExpandRatio(1)
		  	.setHidden(true);

        Label documentsAnnotations = new Label("Select one collection per document");

        documentGridComponent = new ActionGridComponent<TreeGrid<Resource>>(
                documentsAnnotations,
                documentGrid
        );
        documentGridComponent.setSizeFull();
        miToggleResponsibiltityFilter = 
        	documentGridComponent.getActionGridBar().getBtnMoreOptionsContextMenu().addItem(
        			"Hide others' responsibilities", mi -> toggleResponsibilityFilter());
        
        miToggleResponsibiltityFilter.setCheckable(true);
        miToggleResponsibiltityFilter.setChecked(true);

        
        addComponent(documentGridComponent);
	}

	private void toggleResponsibilityFilter() {
		if (!miToggleResponsibiltityFilter.isChecked()) {
			Notification.show(
					"Warning",
					"Selecting collections that are beyond your responsibility "
					+ "might result in conflicts with operations of other project members!",
					Type.WARNING_MESSAGE
			);
		}

		documentGrid.getColumn(DocumentGridColumn.RESPONSIBLE.name()).setHidden(
				miToggleResponsibiltityFilter.isChecked()
		);

		try {
			initData();
		}
		catch (Exception e) {
			((ErrorHandler) UI.getCurrent()).showAndLogError(
					"Error loading available collections", e
			);
		}
	}

	@Override
	public ProgressStep getProgressStep() {
		return progressStep;
	}

	@Override
	public WizardStep getNextStep() {
		return null; // no next step
	}

	@Override
	public boolean isValid() {
        @SuppressWarnings("unchecked")
		Set<String> documentIds = (Set<String>) context.get(AnnotationWizardContextKey.DOCUMENTIDS);

        Map<String, AnnotationCollectionReference> collectionsByDocumentId = 
        		getCollectionRefsByDocumentId();
		
		return collectionsByDocumentId.keySet().containsAll(documentIds);
	}

	private Map<String, AnnotationCollectionReference> getCollectionRefsByDocumentId() {
		Set<Resource> selectedItems = documentGrid.getSelectedItems();
		
		Map<String, AnnotationCollectionReference> collectionsByDocumentId = 
				new HashMap<>();
		for (Resource resource : selectedItems) {
			if (resource instanceof CollectionResource) {
				collectionsByDocumentId.put(
					((CollectionResource) resource).getCollectionReference().getSourceDocumentId(), 
					((CollectionResource) resource).getCollectionReference());
			}
		}
		return collectionsByDocumentId;
	}

	@Override
	public void setStepChangeListener(StepChangeListener stepChangeListener) {
		this.stepChangeListener = stepChangeListener;
	}
	
	@Override
	public void enter(boolean back) {
		eventBus.register(this);
		if (!back && !isValid()) {
	        
	        documentData.getRootItems().forEach(docResource -> {
	        	List<Resource> collectionResources = documentData.getChildren(docResource);
	        	if (collectionResources.size()==1) {
	        		CollectionResource collectionResource = (CollectionResource) collectionResources.get(0);
	        		
	        		if (collectionResource.getCollectionReference().isResponsible(project.getCurrentUser().getIdentifier())) {
	        			documentGrid.select(collectionResource);
	        			documentGrid.expand(docResource);
	        		}
	        	}
	        });			
		}
	}
	
	@Override
	public void exit(boolean back) {
		Map<String, AnnotationCollectionReference> collectionsByDocumentId = 
				getCollectionRefsByDocumentId();
		
		if (back) {
			context.put(AnnotationWizardContextKey.COLLECTIONREFS_BY_DOCID, Collections.emptyMap());
		}
		else {
			context.put(AnnotationWizardContextKey.COLLECTIONREFS_BY_DOCID, collectionsByDocumentId);
		}
		
		eventBus.unregister(this);
	}
	
	@Override
	public boolean canNext() {
		return false;
	}
	
	@Override
	public boolean canFinish() {
		return true;
	}

}
