-- Configure the recurring Stripe test-mode prices for the paid plans.

update public.plans
set
  stripe_product_id = 'prod_VMxuc7m2D8NcG7',
  stripe_price_id = 'price_1UMDynJDQsecEfaDJ1AYYZ9b',
  billing_interval = 'month',
  price = 10.00,
  is_active = true
where name = 'Basic';

update public.plans
set
  stripe_product_id = 'prod_VMxvBLZSGVa7EP',
  stripe_price_id = 'price_1UMDzFJDQsecEfaDNR8KHwWi',
  billing_interval = 'month',
  price = 15.00,
  is_active = true
where name = 'Pro';
