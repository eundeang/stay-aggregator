package com.eundeang.aggregator

import com.eundeang.aggregator.domain.SupplierClient
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.ext.list.withAllParentsOf
import com.lemonappdev.konsist.api.ext.list.withNameEndingWith
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertTrue
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import org.junit.jupiter.api.Test
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

/**
 * 코드가 실제로 CLAUDE.md/docs/architecture.md에 적힌 패키지 규칙을 지키는지
 * 커밋마다 자동으로 검증한다 — "행동이 맞는지"(supplierClientContract)가
 * 아니라 "설계한 구조·의존성 방향대로 짜여 있는지"를 정적으로 확인하는
 * 별개의 하네스. 근거: JOURNAL.md — MappingBatchUpsertService가 mapping/
 * 패키지 규칙을 어긴 걸 사람이 리뷰로 잡았던 사례가 계기.
 */
class ArchitectureTest {
    @Test
    fun `domain은 다른 계층에 의존하지 않는다`() {
        val domain = Layer("domain", "com.eundeang.aggregator.domain..")
        val application = Layer("application", "com.eundeang.aggregator.application..")
        val mapping = Layer("mapping", "com.eundeang.aggregator.mapping..")
        val supplier = Layer("supplier", "com.eundeang.aggregator.supplier..")
        val web = Layer("web", "com.eundeang.aggregator.web..")
        val config = Layer("config", "com.eundeang.aggregator.config..")

        Konsist.scopeFromProduction().assertArchitecture {
            domain.dependsOnNothing()
            mapping.dependsOn(domain)
            supplier.dependsOn(domain)
            application.dependsOn(domain, mapping)
            web.dependsOn(application, domain)
            config.dependsOnNothing()
        }
    }

    @Test
    fun `SupplierClient 구현체는 반드시 Component여야 한다`() {
        // Component가 없으면 Spring이 List of SupplierClient에 자동으로 넣어주지 않아
        // 검색·매핑 동기화에서 조용히 빠지는데, 아무 테스트도 이걸 잡아주지 않는다.
        Konsist
            .scopeFromProduction()
            .classes()
            .withAllParentsOf(SupplierClient::class)
            .assertTrue { it.hasAnnotationOf(Component::class) }
    }

    @Test
    fun `공급사 DTO는 internal이어야 한다`() {
        // 근거: CLAUDE.md "공급사 DTO는 supplier 공급사 밖으로 나가지 않는다"
        Konsist
            .scopeFromProduction()
            .classes()
            .withPackage("com.eundeang.aggregator.supplier..")
            .withNameEndingWith("Dto")
            .assertTrue { it.hasInternalModifier }
    }

    @Test
    fun `mapping 패키지엔 Entity Embeddable Repository만 있어야 한다`() {
        // 근거: JOURNAL.md — MappingBatchUpsertService(비즈니스 로직)가 이 패키지에
        // 잘못 들어갔던 걸 사람이 코드 리뷰로 잡았던 사례. 이후엔 자동 검증.
        Konsist
            .scopeFromProduction()
            .classesAndInterfaces()
            .withPackage("com.eundeang.aggregator.mapping")
            .assertTrue {
                it.hasAnnotationOf(Entity::class) ||
                    it.hasAnnotationOf(Embeddable::class) ||
                    it.hasAnnotationOf(Repository::class) ||
                    it.name.endsWith("Repository")
            }
    }
}
