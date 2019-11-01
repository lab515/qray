package io.github.lab515.qray.runtime;
@Remotable(type = Remotype.UNDEFINED)
public interface Remotee {
    Remoto getStub();
}
