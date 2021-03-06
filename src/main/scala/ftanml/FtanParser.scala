package ftanml

import scala.util.parsing.combinator.RegexParsers
import ftanml.objects.FtanNull
import ftanml.objects.FtanBoolean
import ftanml.objects.FtanNumber
import ftanml.objects.FtanValue
import scala.util.parsing.input.CharSequenceReader
import ftanml.objects.FtanArray
import ftanml.objects.FtanString
import ftanml.objects.FtanElement
import scala.collection.mutable.LinkedHashMap

class FtanParser extends RegexParsers with DebugParser {

  var _skipWhitespace = true
  override def skipWhitespace = _skipWhitespace

  def _null: Parser[FtanNull.type] =
    "null" ^^^ (FtanNull)

  def boolean: Parser[FtanBoolean] =
    ("true" | "false") ^^ {
      value => FtanBoolean(value.toBoolean)
    }

  def number: Parser[FtanNumber] =
    """-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""".r ^^ {
      value => FtanNumber(value.toDouble)
    }

  private def escapedCharacter: Parser[String] =
    "\\" ~> """[bfnrt<>"'\\/]|u[a-fA-F0-9]{4}|x[a-fA-F0-9]+;|\[(.).*?\1\]""".r ^^ {
      value => FtanString.deescapeChar("\\" + value)
    }

  def string: Parser[FtanString] = {
    def stringcontent(usedQuote: Char): Parser[FtanString] = {
      def stringCharacter: Parser[Char] =
        ("[^\\" + usedQuote + "\b\f\n\r\t]").r ^^ { _.charAt(0) }

      ((escapedCharacter | stringCharacter)*) ^^ {
        value => FtanString(("" /: value)(_ + _))
      }
    }
    def quoted_string(quote: Char) = quote.toString ~> stringcontent(quote) <~ quote.toString

    (quoted_string('"') | quoted_string('\''))
  }

  def array: Parser[FtanArray] =
    "[" ~> (repsep(value, ",")) <~ "]" ^^ {
      value => FtanArray(value)
    }

  def element: Parser[FtanElement] = {
    def attributes = new Parser[LinkedHashMap[FtanString, FtanValue]] {
      def nameWithoutQuotes: Parser[FtanString] =
        FtanElement.VALID_NAME ^^ {
          value => FtanString(value)
        }
      def name: Parser[FtanString] = nameWithoutQuotes | string
      def pair: Parser[(FtanString, FtanValue)] =
        name ~ "=" ~ value ^^ {
          case name ~ sep ~ value => (name, value)
        }
      def firstpair: Parser[(FtanString, FtanValue)] =
        (pair | name) ^^ {
          case name: FtanString => FtanElement.NAME_KEY -> name
          case (key: FtanString, value: FtanValue) => (key, value)
        }
      def parser = firstpair ~ (pair*) ^^ {
        case firstpair ~ tailpairs => new LinkedHashMap[FtanString, FtanValue]() += firstpair ++= tailpairs
      }
      override def apply(in: Input) = {
        val _skipWhitespaceStore = _skipWhitespace
        _skipWhitespace = true
        val result = parser(in)
        _skipWhitespace = _skipWhitespaceStore
        result
      }
    }
    def content = new Parser[FtanArray] {
      def contentstringCharacter: Parser[Char] =
        """[^\\<>]""".r ^^ { _.charAt(0) }
      def contentstring: Parser[FtanString] =
        ((escapedCharacter | contentstringCharacter)+) ^^ {
          value => FtanString(("" /: value)(_ + _))
        }
      def parser = ((contentstring | element)*) ^^ {
        value => FtanArray(value)
      }
      override def apply(in: Input) = {
        val _skipWhitespaceStore = _skipWhitespace
        _skipWhitespace = false
        val result = parser(in)
        _skipWhitespace = _skipWhitespaceStore
        result
      }
    }

    "<" ~> (attributes?) ~ (("|" ~> content)?) <~ ">" ^^ {
      case attributes ~ content =>
        val attrMap = attributes.getOrElse(new LinkedHashMap[FtanString, FtanValue])
        content.map { content => attrMap += FtanElement.CONTENT_KEY -> content }
        FtanElement(attrMap)
    }
  }

  def value: Parser[FtanValue] =
    _null | boolean | number | string | array | element

  def parse(exp: String): FtanValue = {
    this.value(new CharSequenceReader(exp)) match {
      case Success(result, rest) =>
        result
      case Failure(msg, rest) =>
        println("FAILURE: " + msg)
        FtanNull
      case Error(msg, rest) =>
        println("ERROR: " + msg)
        FtanNull
    }
  }
}

object MyApp extends App {
  val parser = new FtanParser

  for (line <- io.Source.stdin.getLines()) {
    parser.value(new CharSequenceReader(line + "\n>")) match {
      case parser.Success(result, rest) =>
        println(result.toFtanML)
      case parser.Failure(msg, rest) =>
        println("FAILURE: " + msg)
      case parser.Error(msg, rest) =>
        println("ERROR: " + msg)
    }
  }
}
