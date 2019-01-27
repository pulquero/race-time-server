package io.github.pulquero.racetimeserver;

import io.reactivex.Observable;

public interface RaceTimeServiceSubscriber {
    void subscribeToRaceTimeService(Observable<RaceTimeService> serviceObservable);
    void setRaceTimeServiceManager(RaceTimeServiceManager manager);
}
