package test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * A special ArrayList which keeps the hierarchical order of the elements relative to each other the same when importing other lists.<br>
 * <br>
 *  Example:<br>
 *  List A = { [ E1 E2 ] }<br>
 *  List B = { [ E3 E4 ] }<br>
 *  List C = { [ E5 E6 ] }<br>
 *  List D = { [ E7 E8 ] }<br>
 *  List E = { } <br>
 *  Import A <- B<br>
 *  -> A = { [ E1 E2 ] [ E3 E4 ] }<br>
 *  Import C <- D<br>
 *  -> C = { [ E5 E6 ] [ E7 E8 ] }<br>
 *  <br>
 *  The elements the element groups E1/E2 and E5/E6 are now considered to be on the the 0th order, and the element groups E3/E4 and E7/E8 on the 1th order.<br>
 *  When importing these two lists into an another import order list, element groups of the same order will end up next to each other within the same order group in that list.<br>
 *  <br>
 *  Import E <- A<br>
 *  -> E = { [] [ E1 E2 ] [ E3 E4 ] }<br>
 *  Import E <- C<br>
 *  -> E = { [] [ E1 E2 E5 E6 ] [ E3 E4 E7 E8 ] }<br>
 *  <br>
 *  Note that importing does also increment the elements order number by one, causing the creation of an empty order group in the above example.<br>
 *  This is also the reason why the elements in the first imports (A <- B and C <- D) where not merged to the same group.<br>
 *  
 * @param <E>
 */
public class ImportOrderList<E> extends ArrayList<E> {
	
	private static final long serialVersionUID = -3041514421137460042L;
	
	private List<Integer> orderIndecies = new ArrayList<Integer>();
	
	public ImportOrderList() {
		super();
	}
	
	public ImportOrderList(int capacity) {
		super(capacity);
	}

	public ImportOrderList(ImportOrderList<E> list) {
		super(list);
		this.orderIndecies = new ArrayList<Integer>(list.orderIndecies);
	}
	
	public void importList(ImportOrderList<E> list) {
		ensureCapacity(size() + list.size());
		for (int order = 0; order < list.orderIndecies.size(); order++) {
			List<E> elements = list.orderSubList(order);
			addUnderOrder(order + 1, elements);
		}
	}
	
	public List<E> orderSubList(int order) {
		if (order >= this.orderIndecies.size()) throw new IndexOutOfBoundsException(order);
		int first = this.orderIndecies.get(order);
		int last = this.orderIndecies.size() < order + 1 ? this.orderIndecies.get(order + 1) : size();
		return subList(first, last);
	}
	
	public void addUnderOrder(int order, Collection<E> elements) {
		if (order > this.orderIndecies.size()) throw new IndexOutOfBoundsException(order);
		if (order == this.orderIndecies.size())
			this.orderIndecies.add(size());
		int first = this.orderIndecies.get(order);
		addAll(first, elements);
	}
	
	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		for (int o = 0; o < this.orderIndecies.size(); o++) {
			int i = this.orderIndecies.get(o);
			if (i > index) this.orderIndecies.set(o, i + c.size());
		}
		return super.addAll(index, c);
	}
	
	@Override
	public void add(int index, E element) {
		for (int o = 0; o < this.orderIndecies.size(); o++) {
			int i = this.orderIndecies.get(o);
			if (i > index) this.orderIndecies.set(o, i + 1);
		}
		super.add(index, element);
	}
	
	@Override
	public E remove(int index) {
		for (int o = 0; o < this.orderIndecies.size(); o++) {
			int i = this.orderIndecies.get(o);
			if (i > index) this.orderIndecies.set(o, i - 1);
		}
		return super.remove(index);
	}
	
	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException("remove by value not implemented!");
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException("remove by values not implemented!");
	}
	
	@Override
	public boolean removeIf(Predicate<? super E> filter) {
		throw new UnsupportedOperationException("remove by predicate not implemented!");
	}
	
	@Override
	public void sort(Comparator<? super E> c) {
		throw new UnsupportedOperationException("sort not supported on import order list!");
	}
	
	@Override
	protected void removeRange(int fromIndex, int toIndex) {
		throw new UnsupportedOperationException("remove range not implemented!");
	}
	
}
