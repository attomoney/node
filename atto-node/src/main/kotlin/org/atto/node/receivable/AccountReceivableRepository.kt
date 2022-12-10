package org.atto.node.receivable

import org.atto.commons.AttoHash
import org.atto.node.AttoRepository
import org.springframework.data.repository.kotlin.CoroutineCrudRepository


interface AccountReceivableRepository : CoroutineCrudRepository<AccountReceivable, AttoHash>, AttoRepository {

}