package app.flux.react.app.quiz

import app.flux.stores.quiz.TeamsAndQuizStateStore
import app.models.quiz.config.QuizConfig
import app.models.quiz.QuizState
import app.models.quiz.Team
import hydro.common.I18n
import hydro.common.JsLoggingUtils.logExceptions
import hydro.common.JsLoggingUtils.LogExceptionsCallback
import hydro.flux.action.Dispatcher
import hydro.flux.react.HydroReactComponent
import hydro.flux.react.uielements.Bootstrap
import hydro.flux.react.uielements.HalfPanel
import hydro.flux.react.uielements.PageHeader
import hydro.flux.react.uielements.Table
import hydro.flux.react.ReactVdomUtils.^^
import hydro.flux.react.uielements.Bootstrap.Size
import hydro.flux.react.uielements.Bootstrap.Variant
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.html_<^.<
import org.scalajs.dom

import scala.collection.immutable.Seq

final class TeamEditor(
    implicit pageHeader: PageHeader,
    dispatcher: Dispatcher,
    quizConfig: QuizConfig,
    teamsAndQuizStateStore: TeamsAndQuizStateStore,
    i18n: I18n,
) extends HydroReactComponent {

  // **************** API ****************//
  def apply(): VdomElement = {
    component(Props())
  }

  // **************** Implementation of HydroReactComponent methods ****************//
  override protected val config = ComponentConfig(backendConstructor = new Backend(_), initialState = State())
    .withStateStoresDependency(
      teamsAndQuizStateStore,
      _.copy(
        teams = teamsAndQuizStateStore.stateOrEmpty.teams,
      ))

  // **************** Implementation of HydroReactComponent types ****************//
  protected case class Props()
  protected case class State(
      teams: Seq[Team] = Seq(),
      teamIdToNameInput: Map[Long, String] = Map(),
  ) {
    def nameInput(team: Team): String = {
      if (teamIdToNameInput contains team.id) {
        teamIdToNameInput(team.id)
      } else {
        team.name
      }
    }

    def withNameInput(team: Team, newNameInputValue: String): State = {
      copy(teamIdToNameInput = teamIdToNameInput + (team.id -> newNameInputValue))
    }
  }

  protected class Backend($ : BackendScope[Props, State]) extends BackendBase($) {

    override def render(props: Props, state: State): VdomElement = logExceptions {
      <.span(
        Bootstrap.Row(
          HalfPanel(title = <.span("All teams")) {
            <.span(
              Table(
                tableClasses = Seq("table-teams"),
                tableHeaders = Seq(
                  <.th("Name"),
                  <.th()
                ),
                tableRowDatas = tableRowDatas(state)
              ),
              addButton(state)
            )
          }
        )
      )
    }

    private def tableRowDatas(implicit state: State): Seq[Table.TableRowData] = {
      for (team <- state.teams) yield {
        Table.TableRowData(
          Seq[VdomElement](
            <.td(
              <.form(
                <.input(
                  ^.tpe := "text",
                  ^.name := s"team-name-${team.id}",
                  ^.value := state.nameInput(team),
                  ^^.ifThen(state.nameInput(team) != team.name) {
                    ^.className := "value-has-changed"
                  },
                  ^.autoComplete := "off",
                  ^.onChange ==> { (e: ReactEventFromInput) =>
                    logExceptions {
                      val newString = e.target.value
                      $.modState(_.withNameInput(team, newString))
                    }
                  }
                ),
                " ",
                updateNameButton(team)
              )
            ),
            <.td(
              deleteButton(team)
            )
          ))
      }
    }

    private def addButton(implicit state: State): VdomNode = {
      Bootstrap.Button(Variant.info, tag = <.a)(
        Bootstrap.FontAwesomeIcon("plus"),
        " ",
        "Create new team",
        ^.onClick --> doAdd()
      )
    }

    private def updateNameButton(team: Team)(implicit state: State): VdomNode = {
      Bootstrap.Button(Variant.info, Size.xs, tpe = "submit")(
        ^.disabled := state.nameInput(team) == team.name || state.nameInput(team).isEmpty,
        Bootstrap.FontAwesomeIcon("pencil"),
        ^.onClick ==> { (e: ReactEventFromInput) =>
          e.preventDefault()
          doUpdateName(team, state.nameInput(team))
        }
      )
    }

    private def deleteButton(team: Team)(implicit state: State): VdomNode = {
      Bootstrap.Button(Variant.info, Size.xs, tag = <.a)(
        Bootstrap.FontAwesomeIcon("times"),
        ^.onClick --> doDelete(team)
      )
    }

    private def doAdd()(implicit state: State): Callback = LogExceptionsCallback {
      teamsAndQuizStateStore.addTeam(name = "")
    }

    private def doUpdateName(team: Team, newName: String): Callback = LogExceptionsCallback {
      teamsAndQuizStateStore.updateName(team, newName)
    }

    private def doDelete(team: Team): Callback = LogExceptionsCallback {
      if (dom.window.confirm(s"Are you sure you want to delete '${team.name}'")) {
        teamsAndQuizStateStore.deleteTeam(team)
      }
    }
  }
}
