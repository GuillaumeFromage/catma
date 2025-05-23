package de.catma.ui.module.annotate.resourcepanel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.beust.jcommander.internal.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.vaadin.data.TreeData;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.data.provider.TreeDataProvider;
import com.vaadin.event.selection.SelectionEvent;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.SerializablePredicate;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Grid.Column;
import com.vaadin.ui.Grid.SelectionMode;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.TreeGrid;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.renderers.ButtonRenderer;
import com.vaadin.ui.renderers.ClickableRenderer.RendererClickEvent;
import com.vaadin.ui.renderers.HtmlRenderer;

import de.catma.document.annotation.AnnotationCollectionReference;
import de.catma.document.source.SourceDocumentReference;
import de.catma.project.Project;
import de.catma.project.event.ChangeType;
import de.catma.project.event.CollectionChangeEvent;
import de.catma.project.event.DocumentChangeEvent;
import de.catma.project.event.ProjectReadyEvent;
import de.catma.tag.TagManager.TagManagerEvent;
import de.catma.tag.TagsetDefinition;
import de.catma.ui.component.IconButton;
import de.catma.ui.component.TreeGridFactory;
import de.catma.ui.component.actiongrid.ActionGridComponent;
import de.catma.ui.component.actiongrid.SearchFilterProvider;
import de.catma.ui.dialog.SaveCancelListener;
import de.catma.ui.dialog.SingleTextInputDialog;
import de.catma.ui.events.RefreshEvent;
import de.catma.ui.module.dashboard.GroupMemberParticipant;
import de.catma.ui.module.main.ErrorHandler;
import de.catma.ui.module.project.GroupParticipant;
import de.catma.ui.module.project.ProjectMemberParticipant;
import de.catma.ui.module.project.ProjectParticipant;
import de.catma.user.Member;
import de.catma.user.SharedGroup;
import de.catma.user.SharedGroupMember;
import de.catma.util.IDGenerator;

public class AnnotateResourcePanel extends VerticalLayout {
	
	private Project project;
	private TreeGrid<DocumentTreeItem> documentTree;
	private TreeData<DocumentTreeItem> documentData;
	private ComboBox<ProjectParticipant> cbMembers;	
	private Button btSelectMemberCollections;
	private Button btDeselectMemberCollections;
	private Grid<TagsetDefinition> tagsetGrid;
	private ResourceSelectionListener resourceSelectionListener;
	private ActionGridComponent<TreeGrid<DocumentTreeItem>> documentActionGridComponent;
	private ErrorHandler errorHandler;
	private PropertyChangeListener tagsetChangeListener;
	private PropertyChangeListener tagMovedListener;
	private ListDataProvider<TagsetDefinition> tagsetData;
	private ActionGridComponent<Grid<TagsetDefinition>> tagsetActionGridComponent;
	private EventBus eventBus;

	public AnnotateResourcePanel(Project project, SourceDocumentReference currentlySelectedSourceDocument, EventBus eventBus) {
		super();
		this.project = project;
        this.errorHandler = (ErrorHandler)UI.getCurrent();
        this.eventBus = eventBus;
        eventBus.register(this);
        initProjectListeners();
		
		initComponents();
		initActions();
		this.handleRefresh(new RefreshEvent());
		initData(currentlySelectedSourceDocument, Collections.emptySet());
	}
	
	@Subscribe
	public void handleProjectReadyEvent(ProjectReadyEvent projectReadyEvent) {
		// switch off resourceSelectionListener
		ResourceSelectionListener resourceSelectionListener = this.resourceSelectionListener;
		this.resourceSelectionListener = null;
		
		Collection<TagsetDefinition> tagsets = getSelectedTagsets();
		
		SourceDocumentReference selectedDocumentReference = 
				getSelectedDocument();
		
		initData(
			selectedDocumentReference, 
			Collections.emptySet()); // select all collections visible
		
		tagsetData.getItems().forEach(tagset -> {
			if (tagsets.contains(tagset)) {
				tagsetGrid.select(tagset);
			}
			else {
				tagsetGrid.deselect(tagset);
			}
		});
		
		// switch on resourceSelectionListener
		this.resourceSelectionListener = resourceSelectionListener;
		this.resourceSelectionListener.resourcesChanged();
		
		this.handleRefresh(new RefreshEvent());
	}
	
	@Subscribe
	public void handleRefresh(RefreshEvent refreshEvent) {
		try {
			Set<Member> members = project.getProjectMembers();
			
			Set<SharedGroup> groups = 
					members.stream().filter(member -> member instanceof SharedGroupMember).map(member -> ((SharedGroupMember)member).getSharedGroup()).collect(Collectors.toSet());
			
			
			SortedSet<ProjectParticipant> participants = new TreeSet<ProjectParticipant>((p1, p2) -> p1.toString().compareToIgnoreCase(p2.toString()));
			for (SharedGroup group : groups) {
				participants.add(new GroupParticipant(group));
			}
			
			for (Member member : members) {
				if (member instanceof SharedGroupMember) {					
					participants.add(new GroupMemberParticipant(member));
				}
				else {
					participants.add(new ProjectMemberParticipant(member, true));
				}
			}
			
			cbMembers.setDataProvider(DataProvider.ofCollection(participants));
			
		} catch (IOException e) {
			errorHandler.showAndLogError(String.format("Failed to load members for project %s", project), e);
		}
		
	}

	private void initProjectListeners() {
		tagsetChangeListener = new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				handleTagsetChange(evt);
			}
		};
		tagMovedListener = new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				tagsetData.refreshAll();
			}
		};
		project.getTagManager().addPropertyChangeListener(TagManagerEvent.tagsetDefinitionChanged, tagsetChangeListener);
		project.getTagManager().addPropertyChangeListener(TagManagerEvent.tagDefinitionMoved, tagMovedListener);
	}

	private void handleTagsetChange(PropertyChangeEvent evt) {
		Object oldValue = evt.getOldValue();
		Object newValue = evt.getNewValue();
		
		if (oldValue == null) { // creation
			tagsetData.refreshAll();
		}
		else if (newValue == null) { // removal
			tagsetGrid.deselect((TagsetDefinition) oldValue);
			tagsetData.refreshAll();
		}
		else { // metadata update
			TagsetDefinition tagset = (TagsetDefinition)newValue;
			tagsetData.refreshItem(tagset);
		}
		
    	if (resourceSelectionListener != null) {
    		resourceSelectionListener.tagsetsSelected(getSelectedTagsets());
    	}
	} 
	
	@Subscribe
	public void handleCollectionChanged(CollectionChangeEvent collectionChangeEvent) {
		if (collectionChangeEvent.getChangeType().equals(ChangeType.CREATED)) {
			
    		SourceDocumentReference document = collectionChangeEvent.getDocument();
    		AnnotationCollectionReference collectionReference = 
    				collectionChangeEvent.getCollectionReference();

    		
			CollectionDataItem collectionDataItem = 
				new CollectionDataItem(
					collectionReference, 
					collectionReference.isResponsible(project.getCurrentUser().getIdentifier()));
			documentData.getRootItems()
			.stream()
			.filter(item -> ((DocumentDataItem)item).getDocument().equals(document))
			.findAny().ifPresent(documentDataItem -> {
				documentData.addItem(
	    				documentDataItem, collectionDataItem);
				documentTree.getDataProvider().refreshAll();
			});
			
			if (isAttached()) {
				Notification.show(
					"Info", 
					String.format("Collection \"%s\" has been created", collectionReference),
					Type.TRAY_NOTIFICATION);
			}
			
			if (getSelectedDocument() != null && getSelectedDocument().equals(document)) {
				collectionDataItem.fireSelectedEvent(this.resourceSelectionListener);
			}
    	}
		else if (collectionChangeEvent.getChangeType().equals(ChangeType.DELETED)) {
			Optional<DocumentTreeItem> optionalDocResource = documentData.getRootItems()
			.stream()
			.filter(item -> ((DocumentDataItem)item).getDocument().equals(collectionChangeEvent.getDocument()))
			.findAny();
			
			if (optionalDocResource.isPresent()) {
				documentData.getChildren(optionalDocResource.get()).stream()
				.filter(item -> ((CollectionDataItem)item).getCollectionRef().equals(collectionChangeEvent.getCollectionReference()))
				.findAny()
				.ifPresent(item -> documentData.removeItem(item));
			}
		}
    	else {
    		documentTree.getDataProvider().refreshAll();
    	}
	}
	
	

	private void initActions() {
		documentActionGridComponent.getActionGridBar().addBtnAddClickListener(
				clickEvent -> handleAddCollectionRequest());
		tagsetGrid.addSelectionListener(
				selectionEvent -> handleTagsetSelectionEvent(selectionEvent));
        tagsetActionGridComponent.getActionGridBar().addBtnAddClickListener(
            	click -> handleAddTagsetRequest());		
        tagsetActionGridComponent.setSearchFilterProvider(new SearchFilterProvider<TagsetDefinition>() {
        	@Override
        	public SerializablePredicate<TagsetDefinition> createSearchFilter(final String searchInput) {
        		return new SerializablePredicate<TagsetDefinition>() {
        			@Override
        			public boolean test(TagsetDefinition t) {
        				if (t != null) {
        					String name = t.getName();
        					if (name != null) {
        						return name.toLowerCase().contains(searchInput.toLowerCase());
        					}
        				}
        				return false;
        			}
				};
        	}
		});
        
        btSelectMemberCollections.addClickListener(event -> selectCollectionsForSelectedMembers(true));
        btDeselectMemberCollections.addClickListener(event -> selectCollectionsForSelectedMembers(false));
	}

	private void selectCollectionsForSelectedMembers(boolean selected) {
		ProjectParticipant participant = cbMembers.getValue();
		Set<String> responsableIdentfifiers = Sets.newHashSet();
		if (participant instanceof ProjectMemberParticipant) {
			responsableIdentfifiers.add(((ProjectMemberParticipant)participant).getMember().getIdentifier());
		}
		else if (participant instanceof GroupMemberParticipant) {
			responsableIdentfifiers.add(((GroupMemberParticipant)participant).getMember().getIdentifier());
		}
		else if (participant instanceof GroupParticipant) {
			for (ProjectParticipant p : ((ListDataProvider<ProjectParticipant>)cbMembers.getDataProvider()).getItems()) {
				if (p instanceof GroupMemberParticipant) {
					Member groupMember = ((GroupMemberParticipant) p).getMember();
					if (groupMember instanceof SharedGroupMember) {
						if (((SharedGroupMember) groupMember).getSharedGroup().groupId().equals(participant.getId())) {
							responsableIdentfifiers.add(groupMember.getIdentifier());
						}
					}
				}
			}
		}
		for (DocumentTreeItem documentTreeItem : documentData.getRootItems()) {
			for (DocumentTreeItem collectionItem : documentData.getChildren(documentTreeItem)) {
				String responsableIdentifier = ((CollectionDataItem)collectionItem).getCollectionRef().getResponsibleUser();
				if (responsableIdentfifiers.contains(responsableIdentifier)) {
					collectionItem.setSelected(selected);
					documentTree.getDataProvider().refreshItem(collectionItem);
					collectionItem.fireSelectedEvent(this.resourceSelectionListener);
				}
			}
		}
	}

	private void handleAddTagsetRequest() {
    	
    	SingleTextInputDialog tagsetNameDlg = 
    		new SingleTextInputDialog("Create Tagset", "Please enter the tagset name:",
    				new SaveCancelListener<String>() {
						
						@Override
						public void savePressed(String result) {
							IDGenerator idGenerator = new IDGenerator();
							TagsetDefinition tagset = new TagsetDefinition(
									idGenerator.generateTagsetId(), result);
							tagset.setResponsibleUser(project.getCurrentUser().getIdentifier());
							project.getTagManager().addTagsetDefinition(
								tagset);
						}
					});
        	
        tagsetNameDlg.show();
	}
	
    private void handleTagsetSelectionEvent(SelectionEvent<TagsetDefinition> selectionEvent) {
    	if (resourceSelectionListener != null) {
    		resourceSelectionListener.tagsetsSelected(selectionEvent.getAllSelectedItems());
    	}
    
    }

	private void handleAddCollectionRequest() {
    	Set<DocumentTreeItem> selectedItems = documentTree.getSelectedItems();
    	
    	Set<SourceDocumentReference> selectedDocuments = new HashSet<>();
    	
    	for (DocumentTreeItem resource : selectedItems) {
    		DocumentTreeItem root = documentData.getParent(resource);

    		if (root == null) {
    			root = resource;
    		}
    		
    		DocumentDataItem documentDataItem = (DocumentDataItem)root;
    		selectedDocuments.add(documentDataItem.getDocument());
    	}
    	
    	if (selectedDocuments.isEmpty()) {
    		SourceDocumentReference document = getSelectedDocument();
    		if (document != null) {
    			selectedDocuments.add(document);
    		}
    	}
    	if (selectedDocuments.isEmpty()) {
    		Notification.show("Info", "Please select at least one document first!", Type.HUMANIZED_MESSAGE);
    	}
    	else {
	    	SingleTextInputDialog collectionNameDlg = 
	    		new SingleTextInputDialog("Create Annotation Collection", "Please enter the collection name:",
	    				new SaveCancelListener<String>() {
							
							@Override
							public void savePressed(String result) {
								for (SourceDocumentReference document : selectedDocuments) {
									project.createAnnotationCollection(result, document);
								}
							}
						});
	    	
	    	collectionNameDlg.show();
    	}
    }
    
	private void initData(SourceDocumentReference currentlySelectedSourceDocument, Set<String> currentlysSelectedColletionIds) {
		try {
			documentData = new TreeData<>();
			
			Collection<SourceDocumentReference> documents = project.getSourceDocumentReferences(); 
			
			final SourceDocumentReference preselection = currentlySelectedSourceDocument;
			
			documentData.addRootItems(
				documents
				.stream()
				.map(document -> new DocumentDataItem(
						document, 
						preselection != null && document.equals(preselection))));
			
			DocumentTreeItem preselectedItem = null;
			
			for (DocumentTreeItem documentDataItem : documentData.getRootItems()) {
				if (documentDataItem.isSelected()) {
					preselectedItem = documentDataItem;
				}
				for (AnnotationCollectionReference umcRef : 
					((DocumentDataItem)documentDataItem).getDocument().getUserMarkupCollectionRefs()) {
					documentData.addItem(
						documentDataItem, 
						new CollectionDataItem(
							umcRef,
							(currentlysSelectedColletionIds.isEmpty() || currentlysSelectedColletionIds.contains(umcRef.getId()))
						)
					);
				}
			}
			
			documentTree.setDataProvider(new TreeDataProvider<>(documentData));
			if (preselectedItem != null) {
				documentTree.expand(preselectedItem);
			}
			
			tagsetData = new ListDataProvider<TagsetDefinition>(project.getTagsets());
			tagsetGrid.setDataProvider(tagsetData);
			tagsetData.getItems().forEach(tagsetGrid::select);
			
			documentData
				.getRootItems()
				.stream()
				.filter(documentItem -> documentItem.isSelected())
				.findAny()
				.ifPresent(documentItem -> documentTree.expand(documentItem));
			
		} catch (Exception e) {
			errorHandler.showAndLogError("Error loading data", e);
		}
	}
	
	public List<AnnotationCollectionReference> getSelectedAnnotationCollectionReferences() {
		
		Optional<DocumentTreeItem> optionalDocumentTreeItem = 
				documentData.getRootItems()
				.stream()
				.filter(documentTreeItem->documentTreeItem.isSelected())
				.findFirst();
		
		if (optionalDocumentTreeItem.isPresent()) {
			return documentData.getChildren(optionalDocumentTreeItem.get())
				.stream()
				.filter(documentTreeItem -> documentTreeItem.isSelected())
				.map(CollectionDataItem.class::cast)
				.map(collectionDataItem -> collectionDataItem.getCollectionRef())
				.collect(Collectors.toList());
		}
		
		return Collections.emptyList();
	}
	
	public Collection<TagsetDefinition> getSelectedTagsets() {
		return tagsetGrid.getSelectedItems();
	}

	private void initComponents() {
		setWidth("800px");
		addStyleName("annotate-resource-panel");
		Label documentTreeLabel = new Label("Documents & Annotations");
		documentTree = TreeGridFactory.createDefaultTreeGrid();
		documentTree.addStyleNames(
				"resource-grid", 
				"flat-undecorated-icon-buttonrenderer");
		documentTree.setWidth("100%");
		ButtonRenderer<DocumentTreeItem> documentSelectionRenderer = 
				new ButtonRenderer<DocumentTreeItem>(
					documentSelectionClick -> handleVisibilityClickEvent(documentSelectionClick));
		documentSelectionRenderer.setHtmlContentAllowed(true);
		Column<DocumentTreeItem, String> selectionColumn = 
			documentTree.addColumn(
				documentTreeItem -> documentTreeItem.getSelectionIcon(),
				documentSelectionRenderer)
			.setWidth(100);
		
		documentTree.setHierarchyColumn(selectionColumn);
		
		documentTree
			.addColumn(documentTreeItem -> documentTreeItem.getName())
			.setCaption("Name")
			.setWidth(300);
		
		documentTree.setHeight("250px");
			
		documentTree.addColumn(
				documentTreeItem -> documentTreeItem.getIcon(), new HtmlRenderer())
		.setExpandRatio(1);

		documentActionGridComponent = 
				new ActionGridComponent<TreeGrid<DocumentTreeItem>>(documentTreeLabel, documentTree);
		documentActionGridComponent.getActionGridBar().setMoreOptionsBtnVisible(false);
		documentActionGridComponent.addStyleName("annotate-resource-panel__document-grid");

		String toggleVisibilityDescription = "Select a member or user group from the list and use the visibility buttons to toggle the visibility "
				+ "of all collections belonging to the selected member.";

		cbMembers = new ComboBox<ProjectParticipant>("toggle visibility by member");
		cbMembers.addStyleName("annotate-resource-panel__member-box");
		cbMembers.setItemCaptionGenerator(item -> item.getName());
		cbMembers.setItemIconGenerator(item -> item.getIconAsResource());
		cbMembers.setDescription(toggleVisibilityDescription);

		btSelectMemberCollections = new IconButton(VaadinIcons.EYE);
		btSelectMemberCollections.setDescription(toggleVisibilityDescription);
		btSelectMemberCollections.addStyleName("annotate-resource-panel__toggle-visibility-button");

		btDeselectMemberCollections = new IconButton(VaadinIcons.EYE_SLASH);
		btDeselectMemberCollections.setDescription(toggleVisibilityDescription);
		btDeselectMemberCollections.addStyleName("annotate-resource-panel__toggle-visibility-button");

		documentActionGridComponent.getActionGridBar().addComponentAfterSearchField(btDeselectMemberCollections);
		documentActionGridComponent.getActionGridBar().addComponentAfterSearchField(btSelectMemberCollections);
		documentActionGridComponent.getActionGridBar().addComponentAfterSearchField(cbMembers);
		
		addComponent(documentActionGridComponent);
		
		Label tagsetLabel = new Label("Tagsets");
		
		tagsetGrid = new Grid<>();
		tagsetGrid.addStyleNames(
				"resource-grid", 				
				"flat-undecorated-icon-buttonrenderer",
				"no-focused-before-border");

		tagsetGrid.setHeight("250px");
		tagsetGrid.setWidth("100%");
		tagsetGrid
			.addColumn(tagset -> tagset.getName())
			.setCaption("Name")
			.setWidth(300);
		
		tagsetGrid
			.addColumn(tagset -> VaadinIcons.TAGS.getHtml(), new HtmlRenderer())
			.setExpandRatio(1);
		
		tagsetActionGridComponent = 
				new ActionGridComponent<Grid<TagsetDefinition>>(tagsetLabel, tagsetGrid);
		tagsetActionGridComponent.setSelectionModeFixed(SelectionMode.MULTI);
		tagsetActionGridComponent.getActionGridBar().setMoreOptionsBtnVisible(false);
		tagsetActionGridComponent.getActionGridBar().setMargin(new MarginInfo(false, false, false, true));
		
		addComponent(tagsetActionGridComponent);
	}

	private void handleVisibilityClickEvent(RendererClickEvent<DocumentTreeItem> documentSelectionClick) {
		DocumentTreeItem selectedItem = documentSelectionClick.getItem();
		handleVisibilityClickItem(selectedItem);
	}
	
	private void handleVisibilityClickItem(DocumentTreeItem selectedItem) {
		if (!selectedItem.isSelected() || !selectedItem.isSingleSelection()) {
			selectedItem.setSelected(!selectedItem.isSelected());
			
			if (selectedItem.isSingleSelection()) {
				for (DocumentTreeItem item : documentData.getRootItems()) {
					if (!item.equals(selectedItem)) {
						item.setSelected(false);
						documentTree.collapse(item);
					}
					else {
						documentTree.expand(item);
					}
				}
			}		
			documentTree.getDataProvider().refreshAll();
			
			selectedItem.fireSelectedEvent(this.resourceSelectionListener);
		}
	}
	
	public void selectCollectionVisible(String collectionId) {
		documentData.getRootItems()
		.stream()
		.filter(documentTreeItem->documentTreeItem.isSelected())
		.findFirst()
		.ifPresent(
			documentItem -> selectCollectionVisible(documentItem, collectionId));
		
	}

	private void selectCollectionVisible(DocumentTreeItem documentItem, String collectionId) {
		documentData.getChildren(documentItem)
		.stream()
		.filter(item -> ((CollectionDataItem)item).getCollectionRef().getId().equals(collectionId))
		.findFirst()
		.ifPresent(collectionItem -> handleVisibilityClickItem(collectionItem));
	}

	public void setSelectionListener(
			ResourceSelectionListener resourceSelectionListener) {
		this.resourceSelectionListener = resourceSelectionListener;
	}
	
    @Subscribe
    public void handleDocumentChanged(DocumentChangeEvent documentChangeEvent) {
    	SourceDocumentReference currentlySelectedDocument = getSelectedDocument();
    	SourceDocumentReference nextSelectedDocument = null;
    	if ((currentlySelectedDocument != null)
    			&& !(documentChangeEvent.getChangeType().equals(ChangeType.DELETED)
    					&& documentChangeEvent.getDocument().equals(currentlySelectedDocument))) {
    		nextSelectedDocument = currentlySelectedDocument;
    	}
    	
    	initData(nextSelectedDocument, Collections.emptySet());
    }
    
    private SourceDocumentReference getSelectedDocument() {
    	for (DocumentTreeItem documentTreeItem : documentData.getRootItems()) {
    		if ((documentTreeItem instanceof DocumentDataItem) && documentTreeItem.isSelected()) {
    			return ((DocumentDataItem)documentTreeItem).getDocument();
    		}
    	}
    	
    	return null;
    }
    
    public void setSelectedDocument(SourceDocumentReference sourceDocumentReference) {
    	SourceDocumentReference selected = getSelectedDocument();
    	if ((selected == null) || !selected.equals(sourceDocumentReference)) {
    		for (DocumentTreeItem documentTreeItem : documentData.getRootItems()) {
    			if (documentTreeItem instanceof DocumentDataItem) {
    				DocumentDataItem documentDataItem = (DocumentDataItem)documentTreeItem;
    				if (documentDataItem.getDocument().equals(sourceDocumentReference)) {
    					documentDataItem.setSelected(true);
    					documentTree.getDataProvider().refreshItem(documentDataItem);
    					documentTree.expand(documentDataItem);
    				}
    			}
    		}
    	}
    }

	public void close() {
		if (project != null) {
			project.getTagManager().removePropertyChangeListener(TagManagerEvent.tagsetDefinitionChanged, tagsetChangeListener);
		}

		eventBus.unregister(this);
	}
}
