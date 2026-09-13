package com.example.trading.service;

import com.example.trading.entity.GrowwConfigurationEntity;
import com.example.trading.repository.GrowwConfigurationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A TradingView webhook does not belong to any one application user - it
 * must fan out to EVERY connected Groww user, never just one of them (see
 * TradingEngineService). These tests pin down that GrowwUserResolver
 * returns the full connected set as-is, with no "most recently connected"
 * single-user selection left anywhere in it.
 */
class GrowwUserResolverTest {

    private final GrowwConfigurationRepository repository = mock(GrowwConfigurationRepository.class);
    private final GrowwUserResolver resolver = new GrowwUserResolver(repository);

    private GrowwConfigurationEntity connectedUser(Long userId) {
        GrowwConfigurationEntity entity = GrowwConfigurationEntity.forUser(userId);
        entity.setConnected(true);
        return entity;
    }

    @Test
    void findAllConnectedUserIds_returnsEveryConnectedUser_notJustOne() {
        when(repository.findByConnectedTrueOrderByLastConnectedAtDesc())
                .thenReturn(List.of(connectedUser(3L), connectedUser(1L), connectedUser(2L)));

        List<Long> connected = resolver.findAllConnectedUserIds();

        assertThat(connected).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void findAllConnectedUserIds_noConnectedUsers_returnsEmptyList() {
        when(repository.findByConnectedTrueOrderByLastConnectedAtDesc()).thenReturn(List.of());

        assertThat(resolver.findAllConnectedUserIds()).isEmpty();
    }

    @Test
    void findAllConnectedUserIds_singleConnectedUser_returnsExactlyThatOne() {
        when(repository.findByConnectedTrueOrderByLastConnectedAtDesc()).thenReturn(List.of(connectedUser(42L)));

        assertThat(resolver.findAllConnectedUserIds()).containsExactly(42L);
    }
}
