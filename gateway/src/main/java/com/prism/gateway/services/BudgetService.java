package com.prism.gateway.services;

import com.prism.gateway.exception.BudgetExceededException;
import com.prism.gateway.model.VirtualKey;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BudgetService {

    private final Map<String, BudgetCounter> budgets =
            new ConcurrentHashMap<>();

    /**
     * Checks whether the virtual key has already exhausted
     * its monthly budget.
     */
    public void checkBudget(VirtualKey virtualKey) {

        BudgetCounter counter = getCounter(virtualKey);

        synchronized (counter) {

            resetIfNeeded(counter);

            double budget = virtualKey.getMonthlyBudgetUsd();

            if (counter.getSpentUsd() >= budget) {

                throw new BudgetExceededException(
                        "Monthly budget exceeded"
                );
            }
        }
    }

    /**
     * Records the actual cost of a completed request.
     */
    public void addCost(
            VirtualKey virtualKey,
            double costUsd) {

        BudgetCounter counter = getCounter(virtualKey);

        synchronized (counter) {

            resetIfNeeded(counter);

            double newTotal =
                    counter.getSpentUsd() + costUsd;

            double budget =
                    virtualKey.getMonthlyBudgetUsd();

            if (newTotal > budget) {

                counter.setSpentUsd(newTotal);

                throw new BudgetExceededException(
                        "Monthly budget exceeded"
                );
            }

            counter.setSpentUsd(newTotal);
        }
    }

    /**
     * Gets the current monthly spending.
     */
    public double getSpent(
            VirtualKey virtualKey) {

        BudgetCounter counter =
                getCounter(virtualKey);

        synchronized (counter) {

            resetIfNeeded(counter);

            return counter.getSpentUsd();
        }
    }

    /**
     * Gets the remaining monthly budget.
     */
    public double getRemaining(
            VirtualKey virtualKey) {

        double budget =
                virtualKey.getMonthlyBudgetUsd();

        double spent =
                getSpent(virtualKey);

        return Math.max(0, budget - spent);
    }

    private BudgetCounter getCounter(
            VirtualKey virtualKey) {

        return budgets.computeIfAbsent(
                virtualKey.getVirtualKey(),
                key -> new BudgetCounter()
        );
    }

    private void resetIfNeeded(
            BudgetCounter counter) {

        YearMonth currentMonth =
                YearMonth.now();

        if (!currentMonth.equals(
                counter.getMonth())) {

            counter.setMonth(currentMonth);
            counter.setSpentUsd(0);
        }
    }

    private static class BudgetCounter {

        private YearMonth month =
                YearMonth.now();

        private double spentUsd = 0;

        public YearMonth getMonth() {
            return month;
        }

        public void setMonth(
                YearMonth month) {

            this.month = month;
        }

        public double getSpentUsd() {
            return spentUsd;
        }

        public void setSpentUsd(
                double spentUsd) {

            this.spentUsd = spentUsd;
        }
    }
}