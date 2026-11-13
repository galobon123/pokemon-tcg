package com.utn.pokemontcg.game.domain.model;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.util.List;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class PlayerSetupState {

    private int mulligans;
    private int handSize;
    private int benchCount;
    private int prizeCount;
    private boolean hasActive;
    private List<String> revealedHandCardIds;

    public int mulligans() { return mulligans; }
    public void setMulligans(int mulligans) { this.mulligans = mulligans; }

    public int handSize() { return handSize; }
    public void setHandSize(int handSize) { this.handSize = handSize; }

    public int benchCount() { return benchCount; }
    public void setBenchCount(int benchCount) { this.benchCount = benchCount; }

    public int prizeCount() { return prizeCount; }
    public void setPrizeCount(int prizeCount) { this.prizeCount = prizeCount; }

    public boolean hasActive() { return hasActive; }
    public void setHasActive(boolean hasActive) { this.hasActive = hasActive; }

    public List<String> revealedHandCardIds() { return revealedHandCardIds; }
    public void setRevealedHandCardIds(List<String> revealedHandCardIds) {
        this.revealedHandCardIds = revealedHandCardIds == null ? null : List.copyOf(revealedHandCardIds);
    }
}