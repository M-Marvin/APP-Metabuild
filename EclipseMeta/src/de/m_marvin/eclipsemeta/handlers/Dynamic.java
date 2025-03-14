package de.m_marvin.eclipsemeta.handlers;

import org.eclipse.jface.action.ContributionItem;

public class Dynamic extends ContributionItem {

	public Dynamic() {
		// TODO Auto-generated constructor stub

		for (var e : getParent().getItems()) {
			System.out.println(e);
		}
		
	}

	public Dynamic(String id) {
		super(id);
		
		for (var e : getParent().getItems()) {
			System.out.println(e);
		}
		
		// TODO Auto-generated constructor stub
	}

}
