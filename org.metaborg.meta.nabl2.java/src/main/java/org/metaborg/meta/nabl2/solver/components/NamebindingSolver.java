package org.metaborg.meta.nabl2.solver.components;

import static org.metaborg.meta.nabl2.util.Unit.unit;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.metaborg.meta.nabl2.constraints.messages.IMessageInfo;
import org.metaborg.meta.nabl2.constraints.messages.MessageContent;
import org.metaborg.meta.nabl2.constraints.namebinding.CAssoc;
import org.metaborg.meta.nabl2.constraints.namebinding.CDeclProperty;
import org.metaborg.meta.nabl2.constraints.namebinding.CGAssoc;
import org.metaborg.meta.nabl2.constraints.namebinding.CGDecl;
import org.metaborg.meta.nabl2.constraints.namebinding.CGDirectEdge;
import org.metaborg.meta.nabl2.constraints.namebinding.CGImport;
import org.metaborg.meta.nabl2.constraints.namebinding.CGRef;
import org.metaborg.meta.nabl2.constraints.namebinding.CResolve;
import org.metaborg.meta.nabl2.constraints.namebinding.INamebindingConstraint;
import org.metaborg.meta.nabl2.constraints.namebinding.INamebindingConstraint.CheckedCases;
import org.metaborg.meta.nabl2.constraints.namebinding.ImmutableCDeclProperty;
import org.metaborg.meta.nabl2.constraints.namebinding.ImmutableCGAssoc;
import org.metaborg.meta.nabl2.constraints.namebinding.ImmutableCGDecl;
import org.metaborg.meta.nabl2.constraints.namebinding.ImmutableCGDirectEdge;
import org.metaborg.meta.nabl2.constraints.namebinding.ImmutableCGImport;
import org.metaborg.meta.nabl2.constraints.namebinding.ImmutableCGRef;
import org.metaborg.meta.nabl2.constraints.namebinding.ImmutableCResolve;
import org.metaborg.meta.nabl2.scopegraph.INameResolution;
import org.metaborg.meta.nabl2.scopegraph.IPath;
import org.metaborg.meta.nabl2.scopegraph.IScopeGraph;
import org.metaborg.meta.nabl2.scopegraph.esop.EsopNameResolution;
import org.metaborg.meta.nabl2.scopegraph.esop.EsopScopeGraph;
import org.metaborg.meta.nabl2.scopegraph.terms.Label;
import org.metaborg.meta.nabl2.scopegraph.terms.Namespace;
import org.metaborg.meta.nabl2.scopegraph.terms.Occurrence;
import org.metaborg.meta.nabl2.scopegraph.terms.Paths;
import org.metaborg.meta.nabl2.scopegraph.terms.ResolutionParameters;
import org.metaborg.meta.nabl2.scopegraph.terms.Scope;
import org.metaborg.meta.nabl2.sets.IElement;
import org.metaborg.meta.nabl2.solver.IProperties;
import org.metaborg.meta.nabl2.solver.Properties;
import org.metaborg.meta.nabl2.solver.Solver;
import org.metaborg.meta.nabl2.solver.SolverComponent;
import org.metaborg.meta.nabl2.solver.TypeException;
import org.metaborg.meta.nabl2.solver.UnsatisfiableException;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.Terms.IMatcher;
import org.metaborg.meta.nabl2.terms.Terms.M;
import org.metaborg.meta.nabl2.unification.UnificationException;
import org.metaborg.meta.nabl2.unification.Unifier;
import org.metaborg.meta.nabl2.util.Unit;
import org.metaborg.meta.nabl2.util.functions.PartialFunction0;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class NamebindingSolver extends SolverComponent<INamebindingConstraint> {

    private final ResolutionParameters params;
    private final EsopScopeGraph<Scope, Label, Occurrence> scopeGraph;
    private final Properties<Occurrence> properties;

    private EsopNameResolution<Scope, Label, Occurrence> nameResolution = null;

    private final Set<INamebindingConstraint> deferedBuilds = Sets.newHashSet();
    private final Set<INamebindingConstraint> deferedChecks = Sets.newHashSet();

    public NamebindingSolver(Solver solver, ResolutionParameters params) {
        super(solver);
        this.params = params;
        this.scopeGraph = new EsopScopeGraph<>();
        this.properties = new Properties<>();
    }

    public IScopeGraph<Scope, Label, Occurrence> getScopeGraph() {
        return scopeGraph;
    }

    public INameResolution<Scope, Label, Occurrence> getNameResolution() {
        return nameResolution != null ? nameResolution : INameResolution.empty();
    }

    public IProperties<Occurrence> getProperties() {
        return properties;
    }

    // ------------------------------------------------------------------------------------------------------//

    @Override protected Unit doAdd(INamebindingConstraint constraint) throws UnsatisfiableException {
        if(nameResolution != null) {
            throw new IllegalStateException("Adding constraints after resolution has started.");
        }
        return constraint.matchOrThrow(CheckedCases.of(this::addBuild, this::addBuild, this::addBuild, this::addBuild,
            this::addBuild, this::addCheck, this::addCheck, this::addCheck));
    }

    @Override protected boolean doIterate() throws UnsatisfiableException, InterruptedException {
        if (isPartial()) {
            return false;
        }
        boolean progress = false;
        progress |= doIterate(deferedBuilds, this::solve);
        if(nameResolution == null && deferedBuilds.isEmpty()) {
            progress |= true;
            nameResolution = new EsopNameResolution<>(scopeGraph, params);
        }
        progress |= doIterate(deferedChecks, this::solve);
        return progress;
    }

    @Override protected Iterable<INamebindingConstraint> doFinish(IMessageInfo messageInfo) {
        List<INamebindingConstraint> constraints = Lists.newArrayList();
        deferedBuilds.stream().map(this::find).forEach(constraints::add);
        deferedChecks.stream().map(this::find).forEach(constraints::add);
        return constraints;
    }

    private INamebindingConstraint find(INamebindingConstraint constraint) {
        return constraint.match(INamebindingConstraint.Cases.<INamebindingConstraint>of(
            // @formatter:off
            decl -> ImmutableCGDecl.of(
                        unifier().find(decl.getScope()),
                        unifier().find(decl.getDeclaration()),
                        decl.getMessageInfo().apply(unifier()::find)),
            ref -> ImmutableCGRef.of(
                        unifier().find(ref.getReference()),
                        unifier().find(ref.getScope()),
                        ref.getMessageInfo().apply(unifier()::find)),
            edge -> ImmutableCGDirectEdge.of(
                        unifier().find(edge.getSourceScope()),
                        edge.getLabel(),
                        unifier().find(edge.getTargetScope()),
                        edge.getMessageInfo().apply(unifier()::find)),
            exp -> ImmutableCGAssoc.of(
                        unifier().find(exp.getDeclaration()),
                        exp.getLabel(),
                        unifier().find(exp.getScope()),
                        exp.getMessageInfo().apply(unifier()::find)),
            imp -> ImmutableCGImport.of(
                        unifier().find(imp.getScope()),
                        imp.getLabel(),
                        unifier().find(imp.getReference()),
                        imp.getMessageInfo().apply(unifier()::find)),
            res -> ImmutableCResolve.of(
                        unifier().find(res.getReference()),
                        unifier().find(res.getDeclaration()),
                        res.getMessageInfo().apply(unifier()::find)),
            assoc -> ImmutableCGAssoc.of(
                        unifier().find(assoc.getDeclaration()),
                        assoc.getLabel(),
                        unifier().find(assoc.getScope()),
                        assoc.getMessageInfo().apply(unifier()::find)),
            prop -> ImmutableCDeclProperty.of(
                        unifier().find(prop.getDeclaration()),
                        prop.getKey(),
                        unifier().find(prop.getValue()),
                        prop.getPriority(),
                        prop.getMessageInfo().apply(unifier()::find))
            // @formatter:on
        ));
    }
    
     // ------------------------------------------------------------------------------------------------------//

    private Unit addBuild(INamebindingConstraint constraint) throws UnsatisfiableException {
        if(isPartial() || !solve(constraint)) {
            deferedBuilds.add(constraint);
        }
        return unit;
    }

    private Unit addCheck(CResolve constraint) throws UnsatisfiableException {
        unifier().addActive(constraint.getDeclaration());
        if(isPartial() || !solve(constraint)) {
            deferedChecks.add(constraint);
        }
        return unit;
    }

    private Unit addCheck(CAssoc constraint) throws UnsatisfiableException {
        unifier().addActive(constraint.getScope());
        if(isPartial() || !solve(constraint)) {
            deferedChecks.add(constraint);
        }
        return unit;
    }

    private Unit addCheck(CDeclProperty constraint) throws UnsatisfiableException {
        unifier().addActive(constraint.getValue());
        if(isPartial() || !solve(constraint)) {
            deferedChecks.add(constraint);
        }
        return unit;
    }

    // ------------------------------------------------------------------------------------------------------//

    private boolean solve(INamebindingConstraint constraint) throws UnsatisfiableException {
        return constraint.matchOrThrow(CheckedCases.of(this::solve, this::solve, this::solve, this::solve, this::solve,
            this::solve, this::solve, this::solve));
    }

    private boolean solve(CGDecl c) throws UnsatisfiableException {
        ITerm declTerm = unifier().find(c.getDeclaration());
        if(!declTerm.isGround()) {
            return false;
        }
        ITerm scopeTerm = unifier().find(c.getScope());
        if(!scopeTerm.isGround()) {
            return false;
        }
        Occurrence decl = Occurrence.matcher().match(declTerm).orElseThrow(() -> new TypeException());
        Scope scope = Scope.matcher().match(scopeTerm).orElseThrow(() -> new TypeException());
        scopeGraph.addDecl(scope, decl);
        return true;
    }

    private boolean solve(CGRef c) throws UnsatisfiableException {
        ITerm refTerm = unifier().find(c.getReference());
        if(!refTerm.isGround()) {
            return false;
        }
        ITerm scopeTerm = unifier().find(c.getScope());
        if(!scopeTerm.isGround()) {
            return false;
        }
        Occurrence ref = Occurrence.matcher().match(refTerm).orElseThrow(() -> new TypeException());
        Scope scope = Scope.matcher().match(scopeTerm).orElseThrow(() -> new TypeException());
        scopeGraph.addRef(ref, scope);
        return true;
    }

    private boolean solve(CGDirectEdge de) throws UnsatisfiableException {
        ITerm sourceScopeTerm = unifier().find(de.getSourceScope());
        if(!sourceScopeTerm.isGround()) {
            return false;
        }
        ITerm targetScopeTerm = de.getTargetScope();
        Scope sourceScope = Scope.matcher().match(sourceScopeTerm).orElseThrow(() -> new TypeException());
        scopeGraph.addDirectEdge(sourceScope, de.getLabel(), ImmutableLazyScope.of(targetScopeTerm, unifier()));
        return true;
    }

    private boolean solve(CGAssoc ee) throws UnsatisfiableException {
        ITerm declTerm = unifier().find(ee.getDeclaration());
        if(!declTerm.isGround()) {
            return false;
        }
        ITerm scopeTerm = unifier().find(ee.getScope());
        if(!scopeTerm.isGround()) {
            return false;
        }
        Occurrence decl = Occurrence.matcher().match(declTerm).orElseThrow(() -> new TypeException());
        Scope scope = Scope.matcher().match(scopeTerm).orElseThrow(() -> new TypeException());
        scopeGraph.addAssoc(decl, ee.getLabel(), scope);
        return true;
    }

    private boolean solve(CGImport ie) throws UnsatisfiableException {
        ITerm scopeTerm = unifier().find(ie.getScope());
        if(!scopeTerm.isGround()) {
            return false;
        }
        ITerm refTerm = unifier().find(ie.getReference());
        Scope scope = Scope.matcher().match(scopeTerm).orElseThrow(() -> new TypeException());
        scopeGraph.addImport(scope, ie.getLabel(), ImmutableLazyOccurrence.of(refTerm, unifier()));
        return true;
    }

    private boolean solve(CResolve r) throws UnsatisfiableException {
        if(nameResolution == null) {
            return false;
        }
        ITerm refTerm = unifier().find(r.getReference());
        if(!refTerm.isGround()) {
            return false;
        }
        Occurrence ref = Occurrence.matcher().match(refTerm).orElseThrow(() -> new TypeException());
        Optional<Iterable<IPath<Scope, Label, Occurrence>>> paths = nameResolution.tryResolve(ref);
        if(paths.isPresent()) {
            List<Occurrence> declarations = Paths.pathsToDecls(paths.get());
            switch(declarations.size()) {
                case 0:
                    throw new UnsatisfiableException(r.getMessageInfo()
                        .withDefault(MessageContent.builder().append(ref).append(" does not resolve.").build()));
                case 1:
                    try {
                        unifier().removeActive(r.getDeclaration());
                        unifier().unify(r.getDeclaration(), declarations.get(0));
                    } catch(UnificationException ex) {
                        throw new UnsatisfiableException(r.getMessageInfo().withDefault(ex.getMessageContent()));
                    }
                    return true;
                default:
                    throw new UnsatisfiableException(r.getMessageInfo().withDefault(MessageContent.builder()
                        .append("Resolution of ").append(ref).append(" is ambiguous.").build()));
            }
        } else {
            return false;
        }
    }

    private boolean solve(CAssoc a) throws UnsatisfiableException {
        if(nameResolution == null) {
            return false;
        }
        ITerm declTerm = unifier().find(a.getDeclaration());
        if(!declTerm.isGround()) {
            return false;
        }
        Occurrence decl = Occurrence.matcher().match(declTerm).orElseThrow(() -> new TypeException());
        Label label = a.getLabel();
        List<Scope> scopes = Lists.newArrayList(scopeGraph.getAssocScopes(decl).get(label));
        switch(scopes.size()) {
            case 0:
                throw new UnsatisfiableException(a.getMessageInfo().withDefault(MessageContent.builder().append(decl)
                    .append(" has no ").append(label).append(" associated scope.").build()));
            case 1:
                try {
                    unifier().removeActive(a.getScope());
                    unifier().unify(a.getScope(), scopes.get(0));
                } catch(UnificationException ex) {
                    throw new UnsatisfiableException(a.getMessageInfo().withDefault(ex.getMessageContent()));
                }
                return true;
            default:
                throw new UnsatisfiableException(a.getMessageInfo().withDefault(MessageContent.builder().append(decl)
                    .append(" has multiple ").append(label).append(" associated scope.").build()));
        }
    }

    private boolean solve(CDeclProperty c) throws UnsatisfiableException {
        ITerm declTerm = unifier().find(c.getDeclaration());
        if(!declTerm.isGround()) {
            return false;
        }
        ITerm keyTerm = unifier().find(c.getKey());
        if(!keyTerm.isGround()) {
            return false;
        }
        Occurrence decl = Occurrence.matcher().match(declTerm).orElseThrow(() -> new TypeException());
        unifier().removeActive(c.getValue());
        Optional<ITerm> prev = properties.putValue(decl, keyTerm, c.getValue());
        if(prev.isPresent()) {
            try {
                unifier().unify(c.getValue(), prev.get());
            } catch(UnificationException ex) {
                throw new UnsatisfiableException(c.getMessageInfo().withDefault(ex.getMessageContent()));
            }
        }
        return true;
    }

    // the two Lazy* classes below are used in place of direct lambda's because lambda's capture the lexical context,
    // and trip up serialization

    @Value.Immutable
    @Serial.Version(value = 42L)
    abstract static class LazyScope implements PartialFunction0<Scope> {

        @Value.Parameter public abstract ITerm getTerm();

        @Value.Parameter public abstract Unifier getUnifier();

        @Override public Optional<Scope> apply() {
            return Optional.of(getUnifier().find(getTerm())).filter(ITerm::isGround)
                .map(t -> Scope.matcher().match(t).orElseThrow(() -> new TypeException()));
        }

    }

    @Value.Immutable
    @Serial.Version(value = 42L)
    abstract static class LazyOccurrence implements PartialFunction0<Occurrence> {

        @Value.Parameter public abstract ITerm getTerm();

        @Value.Parameter public abstract Unifier getUnifier();

        @Override public Optional<Occurrence> apply() {
            return Optional.of(getUnifier().find(getTerm())).filter(ITerm::isGround)
                .map(t -> Occurrence.matcher().match(t).orElseThrow(() -> new TypeException()));
        }

    }

    // ------------------------------------------------------------------------------------------------------//

    public IMatcher<Set<IElement<ITerm>>> nameSets() {
        return term -> {
            if(NamebindingSolver.this.nameResolution == null) {
                return Optional.empty();
            }
            return M.<Optional<Set<IElement<ITerm>>>>cases(
                // @formatter:off
                M.appl2("Declarations", Scope.matcher(), Namespace.matcher(), (t, scope, ns) -> {
                    Iterable<Occurrence> decls = NamebindingSolver.this.scopeGraph.getDecls(scope);
                    return Optional.of(makeSet(decls, ns));
                }),
                M.appl2("References", Scope.matcher(), Namespace.matcher(), (t, scope, ns) -> {
                    Iterable<Occurrence> refs = NamebindingSolver.this.scopeGraph.getRefs(scope);
                    return Optional.of(makeSet(refs, ns));
                }),
                M.appl2("Visibles", Scope.matcher(), Namespace.matcher(), (t, scope, ns) -> {
                    Optional<Iterable<IPath<Scope,Label,Occurrence>>> paths = NamebindingSolver.this.nameResolution.tryVisible(scope);
                    return paths.map(ps -> makeSet(Paths.pathsToDecls(ps), ns));
                }),
                M.appl2("Reachables", Scope.matcher(), Namespace.matcher(), (t, scope, ns) -> {
                    Optional<Iterable<IPath<Scope,Label,Occurrence>>> paths = NamebindingSolver.this.nameResolution.tryReachable(scope);
                    return paths.map(ps -> makeSet(Paths.pathsToDecls(ps), ns));
                })
                // @formatter:on
            ).match(term).flatMap(o -> o);
        };
    }

    private Set<IElement<ITerm>> makeSet(Iterable<Occurrence> occurrences, Namespace namespace) {
        Set<IElement<ITerm>> result = Sets.newHashSet();
        for(Occurrence occurrence : occurrences) {
            if(namespace.getName().isEmpty() || namespace.equals(occurrence.getNamespace())) {
                result.add(new OccurrenceElement(occurrence));
            }
        }
        return result;
    }

    private static class OccurrenceElement implements IElement<ITerm> {

        private final Occurrence occurrence;

        public OccurrenceElement(Occurrence occurrence) {
            this.occurrence = occurrence;
        }

        @Override public ITerm getValue() {
            return occurrence;
        }

        @Override public ITerm getPosition() {
            return occurrence.getIndex();
        }

        @Override public Object project(String name) {
            switch(name) {
                case "name":
                    return occurrence.getName();
                default:
                    throw new IllegalArgumentException("Projection " + name + " undefined for occurrences.");
            }
        }

    }

}