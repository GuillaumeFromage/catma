package de.catma.indexer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TagsetDefinitionUpdateLog {
	
	private Set<byte[]> updatedTagDefinitionUuids = new HashSet<byte[]>();
	private Set<byte[]> deletedTagDefinitionUuids = new HashSet<byte[]>();
	private Set<byte[]> updatedPropertyDefinitionUuids = new HashSet<byte[]>();
	private Set<byte[]> deletedPropertyDefinitionUuids = new HashSet<byte[]>();
	public Set<byte[]> getUpdatedTagDefinitionUuids() {
		return updatedTagDefinitionUuids;
	}
	public void setUpdatedTagDefinitionUuids(Set<byte[]> updatedTagDefinitionUuids) {
		this.updatedTagDefinitionUuids = updatedTagDefinitionUuids;
	}
	public Set<byte[]> getDeletedTagDefinitionUuids() {
		return deletedTagDefinitionUuids;
	}
	public void setDeletedTagDefinitionUuids(Set<byte[]> deletedTagDefinitionUuids) {
		this.deletedTagDefinitionUuids = deletedTagDefinitionUuids;
	}
	public Set<byte[]> getUpdatedPropertyDefinitionUuids() {
		return updatedPropertyDefinitionUuids;
	}
	public Set<byte[]> getDeletedPropertyDefinitionUuids() {
		return deletedPropertyDefinitionUuids;
	}
	
	public void addUpdatedPropertyDefinition(byte[] propDefUuid) {
		updatedPropertyDefinitionUuids.add(propDefUuid);
	}
	
	public void addUpdatedTagDefinition(byte[] tagDefUuid) {
		updatedTagDefinitionUuids.add(tagDefUuid);
	}
	
	public void addDeletedPropertyDefinitions(Collection<byte[]> propDefUuids) {
		deletedPropertyDefinitionUuids.addAll(propDefUuids);
	}
	
}
