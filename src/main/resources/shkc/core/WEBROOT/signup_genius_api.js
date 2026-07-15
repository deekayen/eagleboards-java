// ------------------------------------------------------------------------
// -- $Id: signup_genius_api.js,v 1.1 2017/09/13 18:44:36 sking Exp $
// ------------------------------------------------------------------------
//-- Copyright (c) 2001-2010 by Monfox, LLC.  ALL RIGHTS RESERVED
//--
//-- This software, and the ideas, mechanisms and algorithms expressed
//-- therein, is the intellectual and material property of Monfox, LLC
//-- and is provided for use under applicable license agreement only.
//-- No title to or ownership of the software is hereby transferred. No
//-- license to copy, modify, distribute, translate, decompile, reverse
//-- engineer or otherwise remanufacture this software except under the
//-- above license is granted.
//------------------------------------------------------------------------

var SIGNUP_GENIUS_KEY = "REPLACE_WITH_SIGNUP_GENIUS_KEY";

function signup_genius_get_signupid()
{
   var r = window.dhx4.ajax.getSync("https://api.signupgenius.com/v2/k/signups/created/active/?user_key=" + SIGNUP_GENIUS_KEY);
   if (r != null)
   {
      var result = window.dhx4.s2j(r.xmlDoc.responseText); // convert response to json object); // script will wait for response
      console.log(result);
      alert(result);
   }
}

