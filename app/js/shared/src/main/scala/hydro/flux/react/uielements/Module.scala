package hydro.flux.react.uielements

import app.api.ScalaJsApi.GetInitialDataResponse
import app.flux.stores._
import app.models.user.User
import hydro.common.I18n
import hydro.common.time.Clock
import hydro.flux.action.Dispatcher
import hydro.flux.stores.ApplicationIsOnlineStore
import hydro.flux.stores.PageLoadingStateStore
import hydro.models.access.JsEntityAccess
import app.flux.stores.quiz.TeamsAndQuizStateStore
import app.models.quiz.config.QuizConfig

final class Module(
    implicit i18n: I18n,
    user: User,
    entityAccess: JsEntityAccess,
    globalMessagesStore: GlobalMessagesStore,
    pageLoadingStateStore: PageLoadingStateStore,
    pendingModificationsStore: PendingModificationsStore,
    applicationIsOnlineStore: ApplicationIsOnlineStore,
    teamsAndQuizStateStore: TeamsAndQuizStateStore,
    dispatcher: Dispatcher,
    clock: Clock,
    getInitialDataResponse: GetInitialDataResponse,
    quizConfig: QuizConfig,
) {

  implicit lazy val pageHeader = new PageHeader
  implicit lazy val globalMessages: GlobalMessages = new GlobalMessages
  implicit lazy val pageLoadingSpinner: PageLoadingSpinner = new PageLoadingSpinner
  implicit lazy val applicationDisconnectedIcon: ApplicationDisconnectedIcon = new ApplicationDisconnectedIcon
  implicit lazy val pendingModificationsCounter: PendingModificationsCounter = new PendingModificationsCounter
  implicit lazy val sbadminLayout: SbadminLayout = new SbadminLayout()
}
