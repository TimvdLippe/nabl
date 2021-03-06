package org.metaborg.meta.nabl2.spoofax.primitives;

import java.util.List;
import java.util.Optional;

import org.metaborg.meta.nabl2.scopegraph.terms.ScopeGraphTerms;
import org.metaborg.meta.nabl2.spoofax.TermSimplifier;
import org.metaborg.meta.nabl2.spoofax.analysis.IScopeGraphContext;
import org.metaborg.meta.nabl2.spoofax.analysis.IScopeGraphUnit;
import org.metaborg.meta.nabl2.stratego.TermIndex;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.spoofax.interpreter.core.InterpreterException;

public class SG_debug_scope_graph extends ScopeGraphPrimitive {

    public SG_debug_scope_graph() {
        super(SG_debug_scope_graph.class.getSimpleName(), 0, 1);
    }

    @Override public Optional<? extends ITerm> call(IScopeGraphContext<?> context, ITerm term, List<ITerm> terms)
            throws InterpreterException {
        if(terms.size() != 1) {
            throw new InterpreterException("Need one term argument: analysis");
        }
        return TermIndex.get(terms.get(0)).flatMap(index -> {
            final IScopeGraphUnit unit = context.unit(index.getResource());
            return unit.solution().filter(sol -> unit.isPrimary()).map(sol -> {
                return TermSimplifier.focus(unit.resource(),
                        ScopeGraphTerms.build(sol.getScopeGraph(), sol.getDeclProperties(), sol.getUnifier()));
            });
        });
    }

}