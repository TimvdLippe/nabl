package org.metaborg.meta.nabl2.unification;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.metaborg.meta.nabl2.terms.IListTerm;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.ITermVar;
import org.metaborg.meta.nabl2.terms.ListTerms;
import org.metaborg.meta.nabl2.terms.Terms;
import org.metaborg.meta.nabl2.terms.Terms.M;
import org.metaborg.meta.nabl2.terms.generic.GenericTerms;

import com.google.common.collect.Maps;

public class Unifier implements IUnifier, Serializable {

    private static final long serialVersionUID = 42L;

    private final Map<ITermVar, ITerm> reps;
    private final Map<ITermVar, Integer> sizes;

    public Unifier() {
        this.reps = Maps.newHashMap();
        this.sizes = Maps.newHashMap();
    }

    @Override public Set<ITermVar> getAllVars() {
        return Collections.unmodifiableSet(reps.keySet());
    }

    /**
     * Find representative term.
     */
    public ITerm find(ITerm term) {
        // @formatter:off
        return term.isGround() ? term : term.match(Terms.<ITerm>cases(
            (appl) -> GenericTerms.newAppl(appl.getOp(), appl.getArgs().stream().map(this::find).collect(Collectors.toList()), appl.getAttachments()),
            (list) -> find(list),
            (string) -> string,
            (integer) -> integer,
            (var) -> findVarRep(var)
        ));
        // @formatter:on
    }

    public IListTerm find(IListTerm list) {
        // @formatter:off
        return list.isGround() ? list : list.match(ListTerms.<IListTerm>cases(
            (cons) -> GenericTerms.newCons(find(cons.getHead()), find(cons.getTail()), cons.getAttachments()),
            (nil) -> nil,
            (var) -> (IListTerm) findVarRep(var)
        ));
        // @formatter:on
    }

    private ITerm findVarRep(ITermVar var) {
        if(!reps.containsKey(var)) {
            return var;
        } else {
            ITerm rep = find(reps.get(var));
            reps.put(var, rep);
            return rep;
        }
    }

    /**
     * Find representative term, without recursing on subterms.
     */
    private ITerm findShallow(ITerm term) {
        return M.var(this::findVarRepShallow).match(term).orElse(term);
    }

    private ITerm findVarRepShallow(ITermVar var) {
        if(!reps.containsKey(var)) {
            return var;
        } else {
            ITerm rep = findShallow(reps.get(var));
            reps.put(var, rep);
            return rep;
        }
    }

    /**
     * Unify two terms.
     * 
     * @return Unified term
     */
    public UnificationResult unify(ITerm left, ITerm right) throws UnificationException {
        final UnificationResult result = new UnificationResult();
        if(!unifyTerms(left, right, result)) {
            throw new UnificationException(find(left), find(right));
        }
        return result;
    }

    private boolean unifyTerms(ITerm left, ITerm right, UnificationResult result) {
        ITerm leftRep = findShallow(left);
        ITerm rightRep = findShallow(right);
        if(leftRep.termEquals(rightRep)) {
            return true;
        } else if(leftRep.isGround() && rightRep.isGround()) {
            return false;
        }
        // @formatter:off
        return leftRep.match(Terms.<Boolean>cases(
            applLeft -> M.<Boolean>cases(
                M.appl(applRight -> applLeft.getOp().equals(applRight.getOp()) &&
                                    applLeft.getArity() == applRight.getArity() &&
                                    unifys(applLeft.getArgs(), applRight.getArgs(), result)),
                M.var(varRight -> unifyVarTerm(varRight, applLeft, result))
            ).match(rightRep).orElse(false),
            listLeft -> M.<Boolean>cases(
                M.list(listRight -> listLeft.match(ListTerms.<Boolean> cases(
                    consLeft -> M.<Boolean> cases(
                        M.cons(consRight -> {
                            return unifyTerms(consLeft.getHead(), consRight.getHead(), result) &&
                                   unifyTerms(consLeft.getTail(), consRight.getTail(), result);
                        }),
                        M.var(varRight -> unifyVarTerm(varRight, consLeft, result))
                    ).match(listRight).orElse(false),
                    nilLeft -> M.<Boolean> cases(
                        M.nil(nilRight -> true),
                        M.var(varRight -> unifyVarTerm(varRight, nilLeft, result))
                    ).match(listRight).orElse(false),
                    varLeft -> M.<Boolean> cases(
                        M.var(varRight -> unifyVars(varLeft, varRight, result)),
                        M.term(termRight -> unifyVarTerm(varLeft, termRight, result))
                    ).match(listRight).orElse(false)
                ))),
                M.var(varRight -> unifyTerms(varRight, listLeft, result))
            ).match(rightRep).orElse(false),
            stringLeft -> M.<Boolean>cases(
                M.string(stringRight -> stringLeft.getValue().equals(stringRight.getValue())),
                M.var(varRight -> unifyVarTerm(varRight, stringLeft, result))
            ).match(rightRep).orElse(false),
            integerLeft -> M.<Boolean>cases(
                M.integer(integerRight -> integerLeft.getValue() != integerRight.getValue()),
                M.var(varRight -> unifyVarTerm(varRight, integerLeft, result))
            ).match(rightRep).orElse(false),
            varLeft -> M.cases(
                M.var(varRight -> unifyVars(varLeft, varRight, result)),
                M.term(termRight -> unifyVarTerm(varLeft, termRight, result))
            ).match(rightRep).orElse(false)
        ));
        // @formatter:on
    }

    private boolean unifyVarTerm(ITermVar var, ITerm term, UnificationResult result) {
        if(term.getVars().contains(var)) {
            return false;
        }
        reps.put(var, term);
        result.addSubstituted(var);
        return true;
    }

    private boolean unifyVars(ITermVar varLeft, ITermVar varRight, UnificationResult result) {
        int sizeLeft = sizes.getOrDefault(varLeft, 1);
        int sizeRight = sizes.getOrDefault(varRight, 1);
        if(sizeLeft > sizeRight) {
            reps.put(varRight, varLeft);
            sizes.put(varLeft, sizeLeft + sizeRight);
            result.addSubstituted(varRight);
        } else {
            reps.put(varLeft, varRight);
            sizes.put(varRight, sizeLeft + sizeRight);
            result.addSubstituted(varLeft);
        }
        return true;
    }

    private boolean unifys(Iterable<ITerm> lefts, Iterable<ITerm> rights, UnificationResult result) {
        Iterator<ITerm> itLeft = lefts.iterator();
        Iterator<ITerm> itRight = rights.iterator();
        boolean success = true;
        while(itLeft.hasNext()) {
            if(!itRight.hasNext()) {
                return false;
            }
            success &= unifyTerms(itLeft.next(), itRight.next(), result);
        }
        if(itRight.hasNext()) {
            return false;
        }
        return success;
    }


    public boolean canUnify(ITerm left, ITerm right) {
        return left
                .match(Terms
                        .<Boolean>cases(
            // @formatter:off
            applLeft -> M.<Boolean>cases(
                M.appl(applRight -> (applLeft.getOp().equals(applRight.getOp()) &&
                                     applLeft.getArity() == applLeft.getArity() &&
                                     canUnifys(applLeft.getArgs(), applRight.getArgs()))),
                M.var(varRight -> true)
            ).match(right).orElse(false),
            listLeft -> M.<Boolean>cases(
                M.list(listRight -> listLeft.match(ListTerms.<Boolean> cases(
                    consLeft -> M.<Boolean>cases(
                        M.cons(consRight -> (canUnify(consLeft.getHead(), consRight.getHead()) &&
                                             canUnify(consLeft.getTail(), consRight.getTail()))),
                        M.var(varRight -> true)
                    ).match(listRight).orElse(false),
                    nilLeft -> M.<Boolean>cases(
                        M.nil(nilRight -> true),
                        M.var(varRight -> true)
                    ).match(listRight).orElse(false),
                    varLeft -> true
                ))),
                M.var(varRight -> true)
            ).match(right).orElse(false),
            stringLeft -> M.<Boolean>cases(
                M.string(stringRight -> stringLeft.getValue().equals(stringRight.getValue())),
                M.var(varRight -> true)
            ).match(right).orElse(false),
            integerLeft -> M.<Boolean>cases(
                M.integer(integerRight -> (integerLeft.getValue() == integerRight.getValue())),
                M.var(varRight -> true)
            ).match(right).orElse(false),
            varLeft -> true
            // @formatter:on
        ));
    }

    private boolean canUnifys(Iterable<ITerm> lefts, Iterable<ITerm> rights) {
        Iterator<ITerm> itLeft = lefts.iterator();
        Iterator<ITerm> itRight = rights.iterator();
        while(itLeft.hasNext()) {
            if(!(itRight.hasNext() && canUnify(itLeft.next(), itRight.next()))) {
                return false;
            }
        }
        return !itRight.hasNext();
    }

}