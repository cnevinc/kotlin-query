package io.andygrove.kquery.execution

import io.andygrove.kquery.datasource.DataSource
import io.andygrove.kquery.datasource.RecordBatch
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.*
import org.apache.arrow.vector.types.pojo.Schema
import java.util.*


/**
 * A physical plan represents an executable piece of code that will produce data.
 */
interface PhysicalPlan {

    /**
     * Execute a physical plan and produce a series of record batches.
     */
    fun execute(): Iterable<RecordBatch>
}





