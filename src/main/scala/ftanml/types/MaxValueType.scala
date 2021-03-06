package ftanml.types

import ftanml.objects.{FtanNumber, FtanValue}


/**
 * A type that matches a value less than or equal to a specified maximum numeric value
 */

class MaxValueType(maxValue : FtanNumber, exclusive : Boolean) extends FtanType {
  def matches(value: FtanValue) = {
    value match {
      case v : FtanNumber => if(exclusive) v.value < maxValue.value else v.value <= maxValue.value
      case _ => false
    }
  }
}