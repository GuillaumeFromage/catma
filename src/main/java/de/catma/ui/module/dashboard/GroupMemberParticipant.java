package de.catma.ui.module.dashboard;

import java.time.LocalDate;

import com.vaadin.icons.VaadinIcons;
import com.vaadin.server.FontIcon;
import com.vaadin.server.Resource;

import de.catma.rbac.RBACRole;
import de.catma.ui.module.project.ProjectParticipant;
import de.catma.user.Member;

public class GroupMemberParticipant implements ProjectParticipant {

	private final Member member;
	
	public GroupMemberParticipant(Member member) {
		super();
		this.member = member;
	}

	@Override
	public Long getId() {
		return member.getUserId();
	}

	@Override
	public String getIcon() {
		return ((FontIcon)getIconAsResource()).getHtml();
	}
	
	@Override
	public Resource getIconAsResource() {
		return VaadinIcons.USER;
	}

	@Override
	public RBACRole getRole() {
		return member.getRole();
	}

	@Override
	public boolean isGroup() {
		return false;
	}

	@Override
	public String getName() {
		return member.getName();
	}

	@Override
	public String getDescription() {
		return member.preciseName();
	}

	@Override
	public boolean isDirect() {
		return true;
	}

	public Member getMember() {
		return member;
	}
	
	@Override
	public LocalDate getExpiresAt() {
		return member.getExpiresAt();
	}
	
	@Override
	public String toString() {
		return getName();
	}
}
