package de.catma.ui.modules.project;

import com.vaadin.icons.VaadinIcons;

import de.catma.document.repository.Repository;
import de.catma.document.standoffmarkup.usermarkup.UserMarkupCollectionReference;
import de.catma.rbac.RBACRole;
import de.catma.repository.git.GitMarkupCollectionHandler;

public class CollectionResource implements Resource {

    private final UserMarkupCollectionReference collectionReference;
	private final String projectId;
	private boolean hasWritePermission;

    CollectionResource(UserMarkupCollectionReference userMarkupCollectionReference, String projectId, boolean hasWritePermission){
        this.collectionReference = userMarkupCollectionReference;
        this.projectId = projectId;
        this.hasWritePermission = hasWritePermission;
    }

    @Override
    public String getDetail() {
        return null;
    }

    @Override
    public boolean hasDetail() {
        return false;
    }
    
    @Override
    public String getName() {
        return collectionReference.toString();
    }
    
    @Override
    public String getIcon() {
		return VaadinIcons.NOTEBOOK.getHtml();
    }
    
    public UserMarkupCollectionReference getCollectionReference() {
		return collectionReference;
	}
    
    @Override
    public String toString() {
    	return getName();
    }
    
    @Override
    public void deleteFrom(Repository project) throws Exception {
    	project.delete(collectionReference);
    }
    
    @Override
    public boolean isCollection() {
    	return true;
    }

	@Override
	public String getResourceId() {
		return collectionReference.getId();
	}

	@Override
	public String getProjectId() {
		return projectId;
	}

	@Override
	public String getPermissionIcon() {
		return hasWritePermission?VaadinIcons.UNLOCK.getHtml():VaadinIcons.LOCK.getHtml();
	}

}
