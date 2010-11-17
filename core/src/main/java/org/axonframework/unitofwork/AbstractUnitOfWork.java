/*
 * Copyright (c) 2010. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.unitofwork;

import org.axonframework.domain.AggregateRoot;
import org.axonframework.domain.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Abstract implementation of the UnitOfWork interface. Provides the necessary implementations to support most actions
 * required by any Unit of Work, such as managing registration with the {@link CurrentUnitOfWork} and support for
 * nesting a Unit of Work.
 *
 * @author Allard Buijze
 * @see CurrentUnitOfWork
 * @since 0.7
 */
public abstract class AbstractUnitOfWork implements UnitOfWork {

    private boolean isStarted;
    private UnitOfWork outerUnitOfWork;
    private List<AbstractUnitOfWork> innerUnitsOfWork = new ArrayList<AbstractUnitOfWork>();

    @Override
    public void commit() {
        assertStarted();
        if (outerUnitOfWork == null) {
            performCommit();
        }
    }

    @Override
    public void rollback() {
        rollback(null);
    }

    @Override
    public void rollback(Throwable cause) {
        assertStarted();
        try {
            doRollback(cause);
        } finally {
            clear();
        }
    }

    @Override
    public void start() {
        if (isStarted) {
            throw new IllegalStateException("UnitOfWork is already started");
        } else if (CurrentUnitOfWork.isStarted()) {
            // we're nesting.
            this.outerUnitOfWork = CurrentUnitOfWork.get();
            if (outerUnitOfWork instanceof AbstractUnitOfWork) {
                ((AbstractUnitOfWork) outerUnitOfWork).registerInnerUnitOfWork(this);
            } else {
                outerUnitOfWork.registerListener(new CommitOnOuterCommitTask());
            }
        }
        CurrentUnitOfWork.set(this);
        isStarted = true;
        doStart();
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Performs logic required when starting this UnitOfWork instance.
     * <p/>
     * This implementation does nothing and may be freely overridden.
     */
    protected void doStart() {
    }

    /**
     * Executes the logic required to commit this unit of work.
     */
    protected abstract void doCommit();

    /**
     * Executes the logic required to commit this unit of work.
     *
     * @param cause the cause of the rollback
     */
    protected abstract void doRollback(Throwable cause);

    private void performCommit() {
        try {
            doCommit();
        } catch (RuntimeException t) {
            doRollback(t);
            throw t;
        } finally {
            clear();
        }
    }

    private void assertStarted() {
        if (!isStarted) {
            throw new IllegalStateException("UnitOfWork is not started");
        }
    }

    private void clear() {
        CurrentUnitOfWork.clear(this);
        isStarted = false;
    }

    /**
     * Commit all registered inner units of work. This should be invoked after events have been dispatched and before
     * any listeners are notified of the commit.
     */
    protected void commitInnerUnitOfWork() {
        for (AbstractUnitOfWork unitOfWork : innerUnitsOfWork) {
            if (unitOfWork.isStarted()) {
                CurrentUnitOfWork.set(unitOfWork);
                try {
                    unitOfWork.performCommit();
                } finally {
                    CurrentUnitOfWork.clear(unitOfWork);
                }
            }
        }
    }

    private void registerInnerUnitOfWork(AbstractUnitOfWork unitOfWork) {
        innerUnitsOfWork.add(unitOfWork);
    }

    private class CommitOnOuterCommitTask implements UnitOfWorkListener {

        @Override
        public void afterCommit() {
            CurrentUnitOfWork.set(AbstractUnitOfWork.this);
            try {
                performCommit();
            } finally {
                CurrentUnitOfWork.clear(AbstractUnitOfWork.this);
            }
        }

        @Override
        public void onRollback(Throwable failureCause) {
            CurrentUnitOfWork.set(AbstractUnitOfWork.this);
            try {
                doRollback(failureCause);
            } finally {
                CurrentUnitOfWork.clear(AbstractUnitOfWork.this);
            }
        }

        @Override
        public void onPrepareCommit(Set<AggregateRoot> aggregateRoots, List<Event> events) {
        }

        @Override
        public void onCleanup() {
        }
    }

}
