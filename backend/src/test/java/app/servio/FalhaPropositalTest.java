// 🔵 Falha de propósito (SRV-30, critério 4). Não mergear.
package app.servio;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FalhaPropositalTest {

    @Test
    void falha() {
        Assertions.fail("falha proposital da SRV-30");
    }
}
