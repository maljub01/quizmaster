package hydro.models.access

import hydro.models.modification.EntityModification
import hydro.models.modification.EntityType
import hydro.common.JsLoggingUtils.logExceptions
import hydro.common.Listenable
import hydro.common.Listenable.WritableListenable
import hydro.models.Entity
import hydro.models.access.JsEntityAccess.Listener

import scala.async.Async.async
import scala.async.Async.await
import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class JsEntityAccessImpl()(
    implicit remoteDatabaseProxy: RemoteDatabaseProxy,
    hydroPushSocketClientFactory: HydroPushSocketClientFactory,
) extends JsEntityAccess {

  private var listeners: Seq[Listener] = Seq()
  private var _pendingModifications: PendingModifications =
    PendingModifications(Seq(), persistedLocally = false)
  private var isCallingListeners: Boolean = false
  private val queryBlockingFutures: mutable.Buffer[Future[Unit]] = mutable.Buffer()

  // **************** Getters ****************//
  override def newQuery[E <: Entity: EntityType](): DbResultSet.Async[E] = {
    DbResultSet.fromExecutor(new DbQueryExecutor.Async[E] {
      override def data(dbQuery: DbQuery[E]) = async {
        if (queryBlockingFutures.nonEmpty) {
          await(queryBlockingFutures.last)
        }
        await(remoteDatabaseProxy.queryExecutor[E]().data(dbQuery))
      }
      override def count(dbQuery: DbQuery[E]) = async {
        if (queryBlockingFutures.nonEmpty) {
          await(queryBlockingFutures.last)
        }
        await(remoteDatabaseProxy.queryExecutor[E]().count(dbQuery))
      }
    })
  }

  override def pendingModifications = _pendingModifications

  // **************** Setters ****************//
  override def persistModifications(modifications: Seq[EntityModification]): Future[Unit] = logExceptions {
    require(!isCallingListeners)

    _pendingModifications ++= modifications

    val persistResponse = remoteDatabaseProxy.persistEntityModifications(modifications)
    val listenersInvoked = persistResponse.queryReflectsModificationsFuture flatMap { _ =>
      invokeListenersAsync(_.modificationsAddedOrPendingStateChanged(modifications))
    }

    val queryBlockingFuture = persistResponse.queryReflectsModificationsFuture
    queryBlockingFutures += queryBlockingFuture
    queryBlockingFuture map { _ =>
      queryBlockingFutures -= queryBlockingFuture
    }

    async {
      await(persistResponse.completelyDoneFuture)
      await(listenersInvoked)
    }
  }

  // **************** Other ****************//
  override def registerListener(listener: Listener): Unit = {
    require(!isCallingListeners)

    listeners = listeners :+ listener
  }

  override def deregisterListener(listener: Listener): Unit = {
    require(!isCallingListeners)

    listeners = listeners.filter(_ != listener)
  }

  override def startCheckingForModifiedEntityUpdates(): Unit = {
    remoteDatabaseProxy.startCheckingForModifiedEntityUpdates(modifications => {
      _pendingModifications --= modifications
      invokeListenersAsync(_.modificationsAddedOrPendingStateChanged(modifications))
    })
  }

  // **************** Private helper methods ****************//
  private def invokeListenersAsync(func: Listener => Unit): Future[Unit] = {
    Future {
      logExceptions {
        require(!isCallingListeners)
        isCallingListeners = true
        listeners.foreach(func)
        isCallingListeners = false
      }
    }
  }
}
