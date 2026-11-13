package com.utn.pokemontcg.game.domain.chain;

import com.utn.pokemontcg.game.domain.attack.AttackPatternAnalyzer;
import com.utn.pokemontcg.game.domain.attack.AttackSpec;
import com.utn.pokemontcg.game.domain.exception.GameRuleException;
import org.springframework.http.HttpStatus;

public class SelectionStep implements AttackStep {
    private final AttackPatternAnalyzer attackPatternAnalyzer;

    public SelectionStep() {
        this(new AttackPatternAnalyzer());
    }

    public SelectionStep(AttackPatternAnalyzer attackPatternAnalyzer) {
        this.attackPatternAnalyzer = attackPatternAnalyzer;
    }

    @Override
    public void execute(AttackContext context) {
        context.audit("SelectionStep:start");
        if (context.cancelled()) return;
        if (requiresTargetSelection(context) && (context.targetSlot() == null || context.targetSlot().isBlank())) {
            context.audit("SelectionStep:missing_target");
            throw new GameRuleException("TARGET_REQUIRED", "Este ataque requiere seleccionar un objetivo", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        context.setTargetSelected(true);
        context.audit("SelectionStep:target_selected=" + (context.targetSlot() == null ? "default" : context.targetSlot()));
    }

    private boolean requiresTargetSelection(AttackContext context) {
        AttackSpec spec = attackPatternAnalyzer.analyze(
            context.attackName(),
            context.attackText(),
            context.damage()
        );
        return spec.requirements().stream()
            .anyMatch(requirement -> "SELECT_TARGET".equals(requirement.type()) && requirement.required());
    }
}
