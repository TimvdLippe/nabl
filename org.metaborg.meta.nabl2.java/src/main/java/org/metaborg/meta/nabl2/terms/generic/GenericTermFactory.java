package org.metaborg.meta.nabl2.terms.generic;

import java.util.LinkedList;

import org.metaborg.meta.nabl2.terms.Annotations;
import org.metaborg.meta.nabl2.terms.IApplTerm;
import org.metaborg.meta.nabl2.terms.IConsTerm;
import org.metaborg.meta.nabl2.terms.IIntTerm;
import org.metaborg.meta.nabl2.terms.IListTerm;
import org.metaborg.meta.nabl2.terms.INilTerm;
import org.metaborg.meta.nabl2.terms.IStringTerm;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.ITermFactory;
import org.metaborg.meta.nabl2.terms.ITermVar;
import org.metaborg.meta.nabl2.terms.ITupleTerm;

public class GenericTermFactory implements ITermFactory {

    @Override public IApplTerm newAppl(String op, Iterable<ITerm> args) {
        return ImmutableApplTerm.of(op, args, Annotations.empty());
    }

    @Override public ITupleTerm newTuple(Iterable<ITerm> args) {
        return ImmutableTupleTerm.of(args, Annotations.empty());
    }

    @Override public IListTerm newList(Iterable<ITerm> elems) {
        LinkedList<ITerm> reverse = new LinkedList<>();
        for (ITerm elem : elems) {
            reverse.addFirst(elem);
        }
        IListTerm list = newNil();
        for (ITerm elem : reverse) {
            list = newCons(elem, list);
        }
        return list;
    }

    @Override public IConsTerm newCons(ITerm head, IListTerm tail) {
        return ImmutableConsTerm.of(head, tail, Annotations.empty());
    }

    @Override public INilTerm newNil() {
        return ImmutableNilTerm.of(Annotations.empty());
    }

    @Override public IStringTerm newString(String value) {
        return ImmutableStringTerm.of(value, Annotations.empty());
    }

    @Override public IIntTerm newInt(int value) {
        return ImmutableIntTerm.of(value, Annotations.empty());
    }

    @Override public ITermVar newVar(String resource, String name) {
        return ImmutableTermVar.of(resource, name, Annotations.empty());
    }
    
}