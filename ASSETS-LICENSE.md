# Third-party material

## Car model

`app/src/main/assets/wire/car.json` and `car.bin` are a decimated, re-packed wireframe mesh of
"2020 Nissan 370Z Sport Touring" (https://sketchfab.com/3d-models/2020-nissan-370z-sport-touring-16a10e686dcd4fe3942f904a1879c0e3)
by Ddiaz Design (https://sketchfab.com/ddiaz-design), licensed under CC BY-NC-SA 4.0
(http://creativecommons.org/licenses/by-nc-sa/4.0/).

Changes: interior and engine removed, exterior simplified to ~50k triangles, split into body,
doors, boot and wheels with hinge and axle data, feature edges precomputed. Shared under the
same licence. Non-commercial use only.

## Wireframe renderer

`WireCar.kt` adapts the depth-occluded line technique and the TextureView/GL-thread host from
Celica Dash (https://github.com/Kaito-Yagami/celica-dash), MIT licence:

> Copyright (c) 2026 Kade
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software
> and associated documentation files (the "Software"), to deal in the Software without
> restriction, including without limitation the rights to use, copy, modify, merge, publish,
> distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
> Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or
> substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
> BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
> NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
> DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## Weather

Weather data by Open-Meteo.com (CC BY 4.0).
