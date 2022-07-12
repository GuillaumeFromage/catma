package de.catma.repository.git.managers;

import java.util.Map.Entry;

import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.IndexDiff.StageState;

public class StatusPrinter {

	public static void print(String resourceDescription, Status status, StringBuilder builder) {
		builder.append("Git Status report for Resource: ");
		builder.append(resourceDescription);
		builder.append("\nClean :" + status.isClean());
		builder.append("\nHas uncommitted changes: ");
		builder.append(status.hasUncommittedChanges());
		if (status.hasUncommittedChanges()) {
			builder.append("\n");
			builder.append(status.getUncommittedChanges());
		}
		builder.append("\nAdded: " + status.getAdded());
		builder.append("\nChanged: " + status.getChanged());
		builder.append("\nRemoved: " + status.getRemoved());
		builder.append("\nMissing: " + status.getMissing());
		builder.append("\nModified: " + status.getModified());
		builder.append("\nUntracked: " +  status.getUntracked());
		builder.append("\nUntracted Folders: " + status.getUntrackedFolders());
		
		builder.append("\nConflicting: " + status.getConflicting());
		for (Entry<String, StageState> entry : status.getConflictingStageState().entrySet()) {
			builder.append("\nConflict: " + entry.getKey() + " " + entry.getValue());
		}
		
		builder.append("\nIgnoredNotInIndex: " + status.getIgnoredNotInIndex());
	}
	
}
