package org.metaborg.meta.nabl2.terms.generic;

import java.util.Objects;

import org.immutables.serial.Serial;
import org.immutables.value.Value;
import org.metaborg.meta.nabl2.terms.IListTerm;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.ITermVar;

import io.usethesource.capsule.Set;

@Value.Immutable
@Serial.Version(value = 42L)
public abstract class TermVar extends AbstractTerm implements ITermVar {

    @Value.Parameter @Override public abstract String getResource();

    @Value.Parameter @Override public abstract String getName();

    @Override public boolean isGround() {
        return false;
    }

    @Value.Default @Value.Auxiliary @Override public boolean isLocked() {
        return false;
    }
    
    @Value.Lazy @Override public Set.Immutable<ITermVar> getVars() {
        return Set.Immutable.of(this);
    }

    @Override public <T> T match(ITerm.Cases<T> cases) {
        return cases.caseVar(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(ITerm.CheckedCases<T, E> cases) throws E {
        return cases.caseVar(this);
    }

    @Override public <T> T match(IListTerm.Cases<T> cases) {
        return cases.caseVar(this);
    }

    @Override public <T, E extends Throwable> T matchOrThrow(IListTerm.CheckedCases<T, E> cases) throws E {
        return cases.caseVar(this);
    }

    @Override public int hashCode() {
        return Objects.hash(getResource(), getName());
    }

    @Override public boolean equals(Object other) {
        if(other == null) {
            return false;
        }
        if(!(other instanceof ITermVar)) {
            return false;
        }
        ITermVar that = (ITermVar) other;
        if(!getResource().equals(that.getResource())) {
            return false;
        }
        if(!getName().equals(that.getName())) {
            return false;
        }
        return true;
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder();
        if(isLocked()) {
            sb.append(".");
        }
        sb.append("?");
        sb.append(getResource());
        sb.append("-");
        sb.append(getName());
        return sb.toString();
    }

}