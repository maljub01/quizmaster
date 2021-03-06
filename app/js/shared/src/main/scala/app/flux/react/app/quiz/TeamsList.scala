package app.flux.react.app.quiz

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import app.common.AnswerBullet
import app.flux.react.app.quiz.TeamIcon.colorOf
import hydro.flux.react.ReactVdomUtils.^^
import app.flux.stores.quiz.GamepadStore.GamepadState
import app.flux.stores.quiz.TeamInputStore
import hydro.flux.react.ReactVdomUtils.<<
import app.flux.stores.quiz.TeamsAndQuizStateStore
import app.models.quiz.config.QuizConfig
import app.models.quiz.QuizState
import app.models.quiz.QuizState.Submission
import app.models.quiz.QuizState.Submission.SubmissionValue
import app.models.quiz.Team
import hydro.common.I18n
import hydro.common.JsLoggingUtils.logExceptions
import hydro.common.JsLoggingUtils.LogExceptionsCallback
import hydro.flux.action.Dispatcher
import hydro.flux.react.HydroReactComponent
import hydro.flux.react.uielements.Bootstrap
import hydro.flux.react.uielements.Bootstrap.Size
import hydro.flux.react.uielements.Bootstrap.Variant
import hydro.flux.react.uielements.PageHeader
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.vdom.html_<^.<

import scala.scalajs.js

final class TeamsList(
    implicit pageHeader: PageHeader,
    dispatcher: Dispatcher,
    quizConfig: QuizConfig,
    teamsAndQuizStateStore: TeamsAndQuizStateStore,
    teamInputStore: TeamInputStore,
    i18n: I18n,
) extends HydroReactComponent {

  // **************** API ****************//
  def apply(showMasterControls: Boolean): VdomElement = {
    component(Props(showMasterControls = showMasterControls))
  }

  // **************** Implementation of HydroReactComponent methods ****************//
  override protected val config =
    ComponentConfig(backendConstructor = new Backend(_), initialState = State())
      .withStateStoresDependency(
        teamsAndQuizStateStore,
        _.copy(
          quizState = teamsAndQuizStateStore.stateOrEmpty.quizState,
          teams = teamsAndQuizStateStore.stateOrEmpty.teams,
        ))
      .withStateStoresDependency(
        teamInputStore,
        _.copy(teamIdToGamepadState = teamInputStore.state.teamIdToGamepadState))

  // **************** Implementation of HydroReactComponent types ****************//
  protected case class Props(showMasterControls: Boolean)
  protected case class State(
      quizState: QuizState = QuizState.nullInstance,
      teams: Seq[Team] = Seq(),
      teamIdToGamepadState: Map[Long, GamepadState] = Map(),
  )

  protected class Backend($ : BackendScope[Props, State]) extends BackendBase($) {

    override def render(props: Props, state: State): VdomNode = logExceptions {
      implicit val quizState = state.quizState
      implicit val _ = props
      val maybeQuestion = quizState.maybeQuestion
      val showSubmissionValue = maybeQuestion.exists { question =>
        (
          props.showMasterControls ||
          question.onlyFirstGainsPoints ||
          question.answerIsVisible(quizState.questionProgressIndex)
        )
      }

      <<.ifThen(state.teams.nonEmpty) {
        <.ul(
          ^.className := "teams-list",
          ^^.ifThen(state.teams.size > 4) {
            ^.className := "teams-list-small"
          },
          ^^.ifThen(state.teams.size > 6) {
            ^.className := "teams-list-smaller"
          },
          (for (team <- state.teams) yield {
            val maybeSubmission =
              quizState.submissions.filter(_.teamId == team.id).lastOption

            <.li(
              ^.key := team.id,
              ^.style := js.Dictionary("borderColor" -> TeamIcon.colorOf(team)),
              <.div(
                ^.className := "name",
                team.name,
                " ",
                <<.ifDefined(state.teamIdToGamepadState.get(team.id)) { gamepadState =>
                  <<.ifThen(gamepadState.connected) {
                    Bootstrap.FontAwesomeIcon("gamepad")(
                      ^.className := "gamepad-icon",
                      ^^.ifThen(gamepadState.anyButtonPressed) {
                        ^.className := "pressed"
                      },
                    )
                  }
                },
                " ",
                TeamIcon(team),
              ),
              <.div(
                ^.className := "score",
                <<.ifThen(props.showMasterControls) {
                  Bootstrap
                    .Button()(
                      ^.onClick --> LogExceptionsCallback(
                        teamsAndQuizStateStore.updateScore(team, scoreDiff = -1)).void,
                      Bootstrap.Glyphicon("minus"),
                    )
                },
                " ",
                team.score,
                " ",
                <<.ifThen(props.showMasterControls) {
                  Bootstrap
                    .Button()(
                      ^.onClick --> LogExceptionsCallback(
                        teamsAndQuizStateStore.updateScore(team, scoreDiff = +1)).void,
                      Bootstrap.Glyphicon("plus"),
                    )
                },
              ),
              <.div(
                ^.className := "submission",
                maybeSubmission match {
                  case Some(submission) if showSubmissionValue => revealingSubmissionValueNode(submission)
                  case Some(_)                                 => Bootstrap.FontAwesomeIcon("circle")
                  case None                                    => Bootstrap.FontAwesomeIcon("circle-o")
                },
              ),
            )
          }).toVdomArray
        )
      }
    }

    private def revealingSubmissionValueNode(submission: Submission)(
        implicit quizState: QuizState,
        props: Props,
    ): VdomNode = {
      val correctnessClass = submission.isCorrectAnswer match {
        case Some(true)  => "correct"
        case Some(false) => "incorrect"
        case None        => ""
      }
      val isMostRecentSubmission = quizState.submissions.last.id == submission.id

      <.span(
        submission.value match {
          case SubmissionValue.PressedTheOneButton =>
            <<.ifDefined(quizState.maybeQuestion) {
              question =>
                <.span(
                  <<.ifThen(if (question.onlyFirstGainsPoints) isMostRecentSubmission else true) {
                    maybeMasterSubmissionControls(submission)
                  },
                  <.span(
                    ^.className := correctnessClass,
                    Bootstrap.FontAwesomeIcon("circle"),
                    <<.ifThen(question.onlyFirstGainsPoints && isMostRecentSubmission) {
                      <.span(
                        " ",
                        submission.isCorrectAnswer match {
                          case Some(true)  => i18n("app.correct")
                          case Some(false) => i18n("app.incorrect")
                          case _           => i18n("app.give-your-answer")
                        },
                      )
                    },
                  ),
                )
            }
          case SubmissionValue.MultipleChoiceAnswer(answerIndex) =>
            AnswerBullet.all(answerIndex).toVdomNode.apply(^.className := correctnessClass)
          case SubmissionValue.FreeTextAnswer(answerString) =>
            <.span(
              maybeMasterSubmissionControls(submission),
              <.span(
                ^.className := correctnessClass,
                answerString,
              ),
            )
        },
      )
    }
  }

  private def maybeMasterSubmissionControls(submission: Submission)(
      implicit quizState: QuizState,
      props: Props,
  ): VdomNode = {
    <<.ifThen(props.showMasterControls) {
      Bootstrap.ButtonGroup(
        Bootstrap.Button()(
          ^.disabled := submission.isCorrectAnswer == Some(false),
          ^.onClick --> Callback
            .future(
              teamsAndQuizStateStore
                .setSubmissionCorrectness(submission.id, isCorrectAnswer = false)
                .map(_ => Callback.empty)),
          Bootstrap.FontAwesomeIcon("times"),
        ),
        Bootstrap.Button()(
          ^.disabled := submission.isCorrectAnswer == Some(true),
          ^.onClick --> Callback
            .future(
              teamsAndQuizStateStore
                .setSubmissionCorrectness(submission.id, isCorrectAnswer = true)
                .map(_ => Callback.empty)),
          Bootstrap.FontAwesomeIcon("check"),
        ),
      )
    }
  }
}
